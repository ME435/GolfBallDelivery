package edu.rosehulman.golfballdeliveryfsm_solution;

import java.util.List;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.NumberPicker;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;
import edu.rosehulman.me435.NavUtils;
import edu.rosehulman.me435.RobotActivity;

public class GolfBallDeliveryActivity extends RobotActivity {

  /** An enum used for variables when a ball color needs to be referenced. */
  public enum BallColor {
    NONE, BLUE, RED, YELLOW, GREEN, BLACK, WHITE
  }

  /** An array (of size 3) that stores what color is present in each golf ball
   * stand location. */
  public BallColor[] mLocationColors = new BallColor[] { BallColor.NONE, BallColor.NONE, BallColor.NONE };
  
  /** An enum used for the mState variable that tracks the robot's current state. */
  public enum State {
    READY_FOR_MISSION, NEAR_BALL_SCRIPT,
    DRIVE_TOWARDS_FAR_BALL, ARC_TO_FAR_BALL,
    FAR_BALL_SCRIPT,
    DRIVE_TOWARDS_HOME, ARC_TO_HOME,
    WAITING_FOR_PICKUP,
    SEEKING_HOME,
    RUNNING_VOICE_COMMAND,
  }

  /** Tracks the robot's current state. */
  private State mState;

  // Enum tips from
  // http://stackoverflow.com/questions/5021246/conveniently-map-between-enum-and-int-string
  // enum to int => yourEnum.ordinal()
  // int to enum => EnumType.values()[someInt]
  // String to enum => EnumType.valueOf(yourString)
  // enum to String => yourEnum.name()

  /** String used in Log messages during my debugging. */
  public static final String TAG = "ME435";

  /** TOTALLY optional bit of humor that can be used during voice commands.
   * Probably more functional without a name. */
  public static final String ROBOT_NAME = "Elmo";

  /** Simple boolean that is updated when the Team button is pressed to switch
   * teams. */
  public boolean mOnRedTeam = false;

  // ---------------------- UI Widget References ----------------------
  /** An array (of size 3) that keeps a reference to the 3 balls displayed on the
   * UI. */
  private ImageButton[] mBallImageButtons;

  /** References to the buttons on the UI that can change color. */
  private Button mTeamChangeButton, mGoOrMissionCompleteButton;

  /** An array constants (of size 7) that keeps a reference to the different ball
   * color images resources. */
  // Note, the order is important and must be the same throughout the app.
  private static final int[] BALL_DRAWABLE_RESOURCES = new int[] { R.drawable.none_ball, R.drawable.blue_ball,
      R.drawable.red_ball, R.drawable.yellow_ball, R.drawable.green_ball, R.drawable.black_ball, R.drawable.white_ball };

  /** TextViews that can change values. */
  private TextView mCurrentStateTextView, mStateTimeTextView, mGpsInfoTextView, mSensorOrientationTextView,
      mGuessXYTextView, mLeftDutyCycleTextView, mRightDutyCycleTextView, mMatchTimeTextView;;
  // ---------------------- End of UI Widget References ----------------------

  /** X and Y coordinates of the far ball's location. This must be calculated, but won't change during a match. */
  private double mFarBallGpsX, mFarBallGpsY;

  /** Location of the white ball on the stand.  0 means the white ball is not present. */
  private int mWhiteBallLocation = 0;
  
  /** If the GPS X value is greater than this and we have the white ball, then drop it off. */
  private static final double WHITE_BALL_MIN_GPS_X_FT = 200;

  /** Values used to drive straight. */
  public int mLeftStraightPwmValue = 255, mRightStraightPwmValue = 255;

  /** Multiplier used during seeking to calculate a PWM value based on the turn amount needed. */
  private static final double SEEKING_DUTY_CYCLE_PER_ANGLE_OFF_MULTIPLIER = 3.0;  // units are (PWM value)/degrees

  /** Variable used to cap the slowest PWM duty cycle used while seeking. Pick a value from -255 to 255. */
  private static final int LOWEST_DESIRABLE_SEEKING_DUTY_CYCLE = 50;
  
  /** Constant used during manual voice commands as the default distance to travel. */
  public static final double MANUAL_VOICE_COMMAND_DISTANCE = 8.0;
  
  /** Instance of a helper method class that implements various script driving functions. */
  private Scripts mScripts;

  /** Time when the state began (saved as the number of millisecond since epoch). */
  private long mStateStartTime;

  /** Time when the match began, ie when Go! was pressed (saved as the number of millisecond since epoch). */
  private long mMatchStartTime;

  /** Constant that holds the maximum length of the match (saved in milliseconds). */
  private long MATCH_LENGTH_MS = 180000; // 3 minutes in milliseconds

  /** When driving to a target, as soon as you are within this distance stop seeking and start driving arc. */
  private double TRANSITION_TO_ARC_STRATEGY_RADIUS_FT = 30.0; // When can a script can do the rest.

  /** Method that is called 10 times per second for updates. Note, the setup was done within RobotActivity. */
  public void loop() {
    super.loop(); // Important to call super first so that the RobotActivity loop function is run first.
    // RobotActivity updated the mGuessX and mGuessY already. Here we need to display it.
    mStateTimeTextView.setText("" + getStateTimeMs() / 1000);
    mGuessXYTextView.setText("(" + (int)mGuessX + ", " + (int)mGuessY+ ")");

    // Match timer.
    long matchTimeMs = MATCH_LENGTH_MS;
    long timeRemainingSeconds = MATCH_LENGTH_MS / 1000;
    if (mState != State.READY_FOR_MISSION) {
      matchTimeMs = getMatchTimeMs();
      timeRemainingSeconds = (MATCH_LENGTH_MS - matchTimeMs) / 1000;
      if (getMatchTimeMs() > MATCH_LENGTH_MS) {
        setState(State.READY_FOR_MISSION);
      }
    }
    mMatchTimeTextView.setText(getString(R.string.time_format, timeRemainingSeconds / 60, timeRemainingSeconds % 60));

    switch (mState) {
    case DRIVE_TOWARDS_FAR_BALL:
      seekTargetAt(mFarBallGpsX, mFarBallGpsY);
      // The transition to the next state is done within the onLocationChanged function. No timeout. Never gives up.
      break;
    case DRIVE_TOWARDS_HOME:
      seekTargetAt(0, 0);
      // The transition to the next state is done within the onLocationChanged function. No timeout. Never gives up.
      break;
    case WAITING_FOR_PICKUP:
      if (getStateTimeMs() > 8000) {
        // I did not get picked up. Seek some more.
        setState(State.SEEKING_HOME);
      }
      break;
    case SEEKING_HOME:
      seekTargetAt(0, 0);
      if (getStateTimeMs() > 8000) {
        // Stop moving to try for pickup.
        setState(State.WAITING_FOR_PICKUP);
      }
      if (mGettingFartherAwayCounter > 5) {
        // Optional.  If we are getting farther away the field sensor is just wrong.  Try a new heading.
        mFieldOrientation.setCurrentFieldHeading(mCurrentCalculatedGpsHeading);
      }
      break;
    default:
      // Other states don't need to do anything, but could.
      break;
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_golf_ball_delivery);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    mBallImageButtons = new ImageButton[] { (ImageButton) findViewById(R.id.location_1_image_button),
        (ImageButton) findViewById(R.id.location_2_image_button),
        (ImageButton) findViewById(R.id.location_3_image_button) };
    mTeamChangeButton = (Button) findViewById(R.id.team_change_button);
    mCurrentStateTextView = (TextView) findViewById(R.id.current_state_textview);
    mStateTimeTextView = (TextView) findViewById(R.id.state_time_textview);
    mGpsInfoTextView = (TextView) findViewById(R.id.gps_info_textview);
    mSensorOrientationTextView = (TextView) findViewById(R.id.orientation_textview);
    mGuessXYTextView = (TextView) findViewById(R.id.guess_location_textview);
    mLeftDutyCycleTextView = (TextView) findViewById(R.id.left_duty_cycle_textview);
    mRightDutyCycleTextView = (TextView) findViewById(R.id.right_duty_cycle_textview);
    mMatchTimeTextView = (TextView) findViewById(R.id.match_time_textview);
    mGoOrMissionCompleteButton = (Button) findViewById(R.id.go_or_mission_complete_button);

    // When you start using the real hardware you don't need fake GPS buttons.
    boolean hideFakeGpsButtons = false;
    if (hideFakeGpsButtons) {
      TableLayout fakeGpsButtonTable = (TableLayout)findViewById(R.id.fake_gps_button_table);
      fakeGpsButtonTable.setVisibility(View.GONE);
    }
    
    // setRobotName(ROBOT_NAME); // Optional. Helps avoid false positives, but not required.
    mScripts = new Scripts(this);
    setState(State.READY_FOR_MISSION);
  }

  public void setState(State newState) {
    mStateStartTime = System.currentTimeMillis();
    // Make sure when the match ends that no scheduled timer events from scripts change the FSM.
    if (mState == State.READY_FOR_MISSION && newState != State.NEAR_BALL_SCRIPT) {
      Toast.makeText(this, "Illegal state transition out of READY_FOR_MISSION", Toast.LENGTH_SHORT).show();
      return;
    }
    mCurrentStateTextView.setText(newState.name());
    speak(newState.name().replace("_", " "));
    switch (newState) {
    case READY_FOR_MISSION:
      mGoOrMissionCompleteButton.setBackgroundResource(R.drawable.green_button);
      mGoOrMissionCompleteButton.setText("Go!");
      sendWheelSpeed(0, 0);
      break;
    case NEAR_BALL_SCRIPT:
      updateMissionStrategyVariables();
      mGpsInfoTextView.setText("---"); // Clear GPS display (optional)
      mGuessXYTextView.setText("---"); // Clear guess display (optional)
      mScripts.nearBallScript();
      break;
    case DRIVE_TOWARDS_FAR_BALL:
      // All actions handled in the loop function.
      break;
    case ARC_TO_FAR_BALL:
      arcToLocation(mFarBallGpsX, mFarBallGpsY, State.FAR_BALL_SCRIPT);
      break;
    case FAR_BALL_SCRIPT:
      mScripts.farBallScript();
      break;
    case DRIVE_TOWARDS_HOME:
      // All actions handled in the loop function.
      break;
    case ARC_TO_HOME:
      arcToLocation(0, 0, State.WAITING_FOR_PICKUP);
      break;
    case WAITING_FOR_PICKUP:
      sendWheelSpeed(0, 0);
      //startListening("Give " + ROBOT_NAME + " a command"); // No voice command for now due to a reconnection bug.
      break;
    case SEEKING_HOME:
      mGettingFartherAwayCounter = 0; // Reset the counter to give seeking a fair chance.
      // Actions handled in the loop function.
      break;
    case RUNNING_VOICE_COMMAND:
      Toast.makeText(this, "Voice command for angle " + mVoiceCommandAngle + " distance " + mVoiceCommandDistance,
          Toast.LENGTH_SHORT).show();
      if (mVoiceCommandDistance > 0) {
        // If the distance is positive drive an arc home.
        double[] arcResults = new double[2];
        NavUtils.calculateArcForVoiceCommand(mVoiceCommandAngle, mVoiceCommandDistance, arcResults);
        double turnRadiusFt = arcResults[NavUtils.RESULT_INDEX_RADIUS];
        double arcLengthFt = arcResults[NavUtils.RESULT_INDEX_ARC_LENGTH];
        mScripts.driveArc(turnRadiusFt, arcLengthFt, State.WAITING_FOR_PICKUP);
        Toast.makeText(this, "Radius " + (int) arcResults[NavUtils.RESULT_INDEX_RADIUS] + " arc length "
                + (int) arcResults[NavUtils.RESULT_INDEX_ARC_LENGTH], Toast.LENGTH_SHORT).show();
      } else {
        // Simply drive backwards for negative distances.
        sendWheelSpeed(-mLeftDutyCycle, -mRightDutyCycle);
        double driveTimeMs = ((double) Math.abs(mVoiceCommandDistance) / DEFAULT_SPEED_FT_PER_SEC) * 1000.0;
        Toast.makeText(this, "Drive for " + (long) driveTimeMs + " milliseconds in reverse.", Toast.LENGTH_LONG).show();
        mCommandHandler.postDelayed(new Runnable() {
          @Override
          public void run() {
            setState(State.WAITING_FOR_PICKUP);
          }
        }, (long) driveTimeMs);
      }
      break;
    }
    mState = newState;
  }

  /** Updates the far ball location X and Y as well as the white ball status. */
  private void updateMissionStrategyVariables() {
    mWhiteBallLocation = 0; // Assume there is no white ball present (the default).
    for (int i = 0; i < 3; i++) {
   // Calculate the mFarBallGpsX and mFarBallGpsY values.
      BallColor currentLocationsBallColor = mLocationColors[i];
      if (!mOnRedTeam && (currentLocationsBallColor == BallColor.RED || currentLocationsBallColor == BallColor.GREEN) ||
          mOnRedTeam && (currentLocationsBallColor == BallColor.BLUE || currentLocationsBallColor == BallColor.YELLOW)) {
        // Found the location of interest.
        mFarBallGpsX = 240;
        if ((!mOnRedTeam && currentLocationsBallColor == BallColor.RED) ||
            (mOnRedTeam && currentLocationsBallColor == BallColor.BLUE)) {
          mFarBallGpsY = 50;
        } else {
          mFarBallGpsY = -50;
        }
      }
      if (currentLocationsBallColor == BallColor.WHITE) {
        mWhiteBallLocation = i + 1;
      }
    }
    
  }

  /** Updated the UI with the appropriate ball color and updates the location colors array with the value. */
  public void setLocationToColor(int location, BallColor ballColor) {
    mBallImageButtons[location - 1].setImageResource(BALL_DRAWABLE_RESOURCES[ballColor.ordinal()]);
    mLocationColors[location - 1] = ballColor;
  }
  
  /** Used to get the state time in milliseconds. */
  private long getStateTimeMs() {
    return System.currentTimeMillis() - mStateStartTime;
  }

  /** Used to get the match time in milliseconds. */
  private long getMatchTimeMs() {
    return System.currentTimeMillis() - mMatchStartTime;
  }
  
  // --------------------------- Driving commands ---------------------------

  /**
   * Send the wheel speeds to the robot and updates the TextViews.
   */
  @Override
  public void sendWheelSpeed(int leftDutyCycle, int rightDutyCycle) {
    super.sendWheelSpeed(leftDutyCycle, rightDutyCycle); // Send the values to the 
    mLeftDutyCycleTextView.setText("Left " + leftDutyCycle);
    mRightDutyCycleTextView.setText(rightDutyCycle + " Right");
  }
  
  
  /** 
   * Adjust the PWM duty cycles based on the turn amount needed to point at the target heading.
   * 
   * @param x GPS X value of the target.
   * @param y GPS Y value of the target. */
  private void seekTargetAt(double x, double y) {
    int leftDutyCycle = mLeftStraightPwmValue;
    int rightDutyCycle = mRightStraightPwmValue;
    double targetHeading = NavUtils.getTargetHeading(mGuessX, mGuessY, x, y);
    double leftTurnAmount = NavUtils.getLeftTurnHeadingDelta(mCurrentSensorHeading, targetHeading);
    double rightTurnAmount = NavUtils.getRightTurnHeadingDelta(mCurrentSensorHeading, targetHeading);
    if (leftTurnAmount < rightTurnAmount) {
      leftDutyCycle = mLeftStraightPwmValue - (int)(leftTurnAmount * SEEKING_DUTY_CYCLE_PER_ANGLE_OFF_MULTIPLIER);
      leftDutyCycle = Math.max(leftDutyCycle, LOWEST_DESIRABLE_SEEKING_DUTY_CYCLE);
    } else {
      rightDutyCycle = mRightStraightPwmValue - (int)(rightTurnAmount * SEEKING_DUTY_CYCLE_PER_ANGLE_OFF_MULTIPLIER);
      rightDutyCycle = Math.max(rightDutyCycle, LOWEST_DESIRABLE_SEEKING_DUTY_CYCLE);
    }
    sendWheelSpeed(leftDutyCycle, rightDutyCycle);
  }

  
  /** Creates a script to drive to a target location then change states when complete. */
  public void arcToLocation(double arcTargetX, double arcTargetY, final State nextStateAfterArcCompletes) {
    // Use the x, y, and heading to determine arc radius info.
    // Then use arc radius to set wheel speed and drive time
    double[] arcResults = new double[2];
    NavUtils.calculateArc(mCurrentGpsX, mCurrentGpsY, mCurrentGpsHeading, arcTargetX, arcTargetY, arcResults);
    double turnRadiusFt = arcResults[0];
    double arcLengthFt = arcResults[1];
    mScripts.driveArc(turnRadiusFt, arcLengthFt, nextStateAfterArcCompletes);
  }

  
  // --------------------------- Sensor listeners ---------------------------

/** GPS sensor updates. */
@Override
public void onLocationChanged(double x, double y, double heading, Location location) {
  super.onLocationChanged(x, y, heading, location);
  if (mWhiteBallLocation > 0 && x > WHITE_BALL_MIN_GPS_X_FT) {
    mScripts.removeBallAtLocation(mWhiteBallLocation);
    mWhiteBallLocation = 0;
  }
  String gpsInfo = getString(R.string.xy_format, x, y);
  if (heading <= 180.0 && heading > -180.0) {
    gpsInfo += " " + getString(R.string.degrees_format, heading);
    if (mState == State.DRIVE_TOWARDS_FAR_BALL) {
      double distanceFromTarget = NavUtils.getDistance(mCurrentGpsX, mCurrentGpsY, mFarBallGpsX, mFarBallGpsY);
      double targetHeading = NavUtils.getTargetHeading(x, y, mFarBallGpsX, mFarBallGpsY);
      // Determine if we should drive arc or continue seeking far ball.
      double leftTurnAmount = NavUtils.getLeftTurnHeadingDelta(heading, targetHeading);
      double rightTurnAmount = NavUtils.getRightTurnHeadingDelta(heading, targetHeading);
      if (distanceFromTarget < TRANSITION_TO_ARC_STRATEGY_RADIUS_FT && (leftTurnAmount < 70 || rightTurnAmount < 70)) {
        setState(State.ARC_TO_FAR_BALL); // The next step is handled within setState.
      }
    }
    if (mState == State.DRIVE_TOWARDS_HOME) {
      double distanceFromTarget = NavUtils.getDistance(mCurrentGpsX, mCurrentGpsY, 0, 0);
      double targetHeading = NavUtils.getTargetHeading(x, y, 0, 0);
      // Determine if we should drive arc or continue seeking home.
      double leftTurnAmount = NavUtils.getLeftTurnHeadingDelta(heading, targetHeading);
      double rightTurnAmount = NavUtils.getRightTurnHeadingDelta(heading, targetHeading);
      if (distanceFromTarget < TRANSITION_TO_ARC_STRATEGY_RADIUS_FT && (leftTurnAmount < 70 || rightTurnAmount < 70)) {
        setState(State.ARC_TO_HOME); // The next step within setState.
      }
    }      
  } else {
    gpsInfo += " ?º";
  }
  gpsInfo += "    " + mGpsCounter;
  mGpsInfoTextView.setText(gpsInfo);
}

  /** Field Orientation sensor updates. */
  @Override
  public void onSensorChanged(double fieldHeading, float[] orientationValues) {
    super.onSensorChanged(fieldHeading, orientationValues);
    mSensorOrientationTextView.setText(getString(R.string.degrees_format, fieldHeading));
  }

  /** Using commands via the raw voice data. */
  @Override
  protected void receiveWhatWasHeard(List<String> heard, float[] confidenceScores) {
    super.receiveWhatWasHeard(heard, confidenceScores);
    // Simple manual commands (optional).
    String command = heard.get(0);
    if (command.equalsIgnoreCase("forward") || command.equalsIgnoreCase("left") ||
        command.equalsIgnoreCase("right") || command.equalsIgnoreCase("back")) {
      Toast.makeText(this, "Received a known command " + command, Toast.LENGTH_SHORT).show();
      if (command.equalsIgnoreCase("forward")) {
        sendWheelSpeed(mLeftStraightPwmValue, mRightStraightPwmValue);
      } else if (command.equalsIgnoreCase("left")) {
        sendWheelSpeed(180, 255);
      } else if (command.equalsIgnoreCase("right")) {
        sendWheelSpeed(255, 180);      
      } else if (command.equalsIgnoreCase("back")) {
        sendWheelSpeed(-255, -255);
      }
      double driveTimeMs = (MANUAL_VOICE_COMMAND_DISTANCE / DEFAULT_SPEED_FT_PER_SEC) * 1000.0;
      mCommandHandler.postDelayed(new Runnable() {
        @Override
        public void run() {
          sendWheelSpeed(0, 0);
        }
      }, (long) driveTimeMs);      
    }
  }
  
  /** Using the "angle" + "distance" arc voice command. */
  @Override
  protected void onVoiceCommand(int angle, int distance) {
    super.onVoiceCommand(angle, distance);
    Toast.makeText(this, "Received arc command: Angle was " + angle + "   Distance was " + distance, Toast.LENGTH_LONG).show();
    // Note, mVoiceCommandAngle and mVoiceCommandDistance are updated in RobotActivity.
    setState(State.RUNNING_VOICE_COMMAND);
  }
  
  // --------------- Button Handlers ---------------

  /** Helper method that is called by all three golf ball clicks. */
  private void handleBallClickForLocation(final int location) {
    new DialogFragment() {
      @Override
      public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("What was the real color?").setItems(R.array.ball_colors,
            new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int which) {
                GolfBallDeliveryActivity.this.setLocationToColor(location, BallColor.values()[which]);
              }
            });
        return builder.create();
      }
    }.show(getFragmentManager(), "unused tag");
  }

  /** Click to the far left image button (Location 1). */
  public void handleBallAtLocation1Click(View view) {
    Toast.makeText(this, "handleBallAtLocation1Click", Toast.LENGTH_SHORT).show();
    handleBallClickForLocation(1);
  }

  /** Click to the center image button (Location 2). */
  public void handleBallAtLocation2Click(View view) {
    Toast.makeText(this, "handleBallAtLocation2Click", Toast.LENGTH_SHORT).show();
    handleBallClickForLocation(2);
  }

  /** Click to the far right image button (Location 3). */
  public void handleBallAtLocation3Click(View view) {
    Toast.makeText(this, "handleBallAtLocation3Click", Toast.LENGTH_SHORT).show();
    handleBallClickForLocation(3);
  }

  public void handleTeamChange(View view) {
    Toast.makeText(this, "handleTeamChange", Toast.LENGTH_SHORT).show();
    mBallImageButtons[0].setImageResource(BALL_DRAWABLE_RESOURCES[BallColor.NONE.ordinal()]);
    mBallImageButtons[1].setImageResource(BALL_DRAWABLE_RESOURCES[BallColor.NONE.ordinal()]);
    mBallImageButtons[2].setImageResource(BALL_DRAWABLE_RESOURCES[BallColor.NONE.ordinal()]);
    mLocationColors = new BallColor[] { BallColor.NONE, BallColor.NONE, BallColor.NONE };
    if (mOnRedTeam) {
      mOnRedTeam = false;
      mTeamChangeButton.setBackgroundResource(R.drawable.blue_button);
      mTeamChangeButton.setText("Team Blue");
    } else {
      mOnRedTeam = true;
      mTeamChangeButton.setBackgroundResource(R.drawable.red_button);
      mTeamChangeButton.setText("Team Red");
    }
    // setTeamToRed(mOnRedTeam); // This call is optional. It will reset your GPS and sensor heading values.
  }

  /** Sends a message to Arduino to perform a ball color test. */
  public void handlePerformBallTest(View view) {
    Toast.makeText(this, "handlePerformBallTest", Toast.LENGTH_SHORT).show();
    sendCommand("CUSTOM Perform ball test");

    // During testing you could send fake values.
    // onCommandReceived("L3 Yellow");
    // onCommandReceived("L1 White");
    // onCommandReceived("L2 Green");
  }

  /** Clicks to the red arrow image button that should show a dialog window. */
  public void handleDrivingStraight(View view) {
    Toast.makeText(this, "handleDrivingStraight", Toast.LENGTH_SHORT).show();
    new DialogFragment() {
      @Override
      public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Driving Straight Calibration");
        View dialoglayout = getLayoutInflater().inflate(R.layout.driving_straight_dialog, (ViewGroup) getCurrentFocus());
        builder.setView(dialoglayout);
        final NumberPicker rightDutyCyclePicker = (NumberPicker)dialoglayout.findViewById(R.id.right_pwm_number_picker);
        rightDutyCyclePicker.setMaxValue(255);
        rightDutyCyclePicker.setMinValue(0);
        rightDutyCyclePicker.setValue(mRightStraightPwmValue);
        rightDutyCyclePicker.setWrapSelectorWheel(false);
        final NumberPicker leftDutyCyclePicker = (NumberPicker)dialoglayout.findViewById(R.id.left_pwm_number_picker);
        leftDutyCyclePicker.setMaxValue(255);
        leftDutyCyclePicker.setMinValue(0);
        leftDutyCyclePicker.setValue(mLeftStraightPwmValue);
        leftDutyCyclePicker.setWrapSelectorWheel(false);
        Button doneButton = (Button)dialoglayout.findViewById(R.id.done_button);
        doneButton.setOnClickListener(new OnClickListener() {
          @Override
          public void onClick(View v) {
            Log.d(TAG, "Left = " + leftDutyCyclePicker.getValue() +  "   Right = " + rightDutyCyclePicker.getValue());
            mLeftStraightPwmValue = leftDutyCyclePicker.getValue();
            mRightStraightPwmValue = rightDutyCyclePicker.getValue();
            dismiss();
          }
        });
        final Button testStraightButton = (Button)dialoglayout.findViewById(R.id.test_straight_button);
        testStraightButton.setOnClickListener(new OnClickListener() {
          @Override
          public void onClick(View v) {
            mLeftStraightPwmValue = leftDutyCyclePicker.getValue();
            mRightStraightPwmValue = rightDutyCyclePicker.getValue();
            mScripts.testStraightDrive();
          }
        });        
        return builder.create();
      }
    }.show(getFragmentManager(), "unused tag");
  }

  /** Wrapper method to call sendCommand since in 2014, sendCommand can't be called from the Scripts file. */
  public void sendArmCommand(String command) {
    sendCommand(command);
  }
  
  /** Test GPS point when going to the Far ball (assumes Blue Team heading to red ball). */
  public void handleFakeGpsF0(View view) {
    Toast.makeText(this, "handleFakeGpsF0", Toast.LENGTH_SHORT).show();
    onLocationChanged(239, 50, NO_HEADING, null);  // Amazing GPS value but no heading so ignored.
  }

  public void handleFakeGpsF1(View view) {
    Toast.makeText(this, "handleFakeGpsF1", Toast.LENGTH_SHORT).show();
    onLocationChanged(240 - TRANSITION_TO_ARC_STRATEGY_RADIUS_FT - 1, 50, 0, null);  // Just a bit out of range so ignored.
  }

  public void handleFakeGpsF2(View view) {
    Toast.makeText(this, "handleFakeGpsF2", Toast.LENGTH_SHORT).show();
    onLocationChanged(220, 50, 135, null);  // Within range but the heading is pointing so poorly an arc should be used.
  }

  public void handleFakeGpsF3(View view) {
    Toast.makeText(this, "handleFakeGpsF3", Toast.LENGTH_SHORT).show();
    onLocationChanged(230, 40, 35, null);  // Within range but the heading is pointing so poorly an arc should be used.
  }

  public void handleFakeGpsH0(View view) {
    Toast.makeText(this, "handleFakeGpsH0", Toast.LENGTH_SHORT).show();
    onLocationChanged(165, 0, -180, null);
  }

  public void handleFakeGpsH1(View view) {
    Toast.makeText(this, "handleFakeGpsH1", Toast.LENGTH_SHORT).show();
    onLocationChanged(50, 0, -180, null);
  }

  public void handleFakeGpsH2(View view) {
    Toast.makeText(this, "handleFakeGpsH2", Toast.LENGTH_SHORT).show();
    onLocationChanged(20, 10, -170, null);
  }

  public void handleFakeGpsH3(View view) {
    Toast.makeText(this, "handleFakeGpsH3", Toast.LENGTH_SHORT).show();
  }

  public void handleSetOrigin(View view) {
    Toast.makeText(this, "handleSetOrigin", Toast.LENGTH_SHORT).show();
    mFieldGps.setCurrentLocationAsOrigin();
  }

  public void handleSetXAxis(View view) {
    Toast.makeText(this, "handleSetXAxis", Toast.LENGTH_SHORT).show();
    mFieldGps.setCurrentLocationAsLocationOnXAxis();
  }

  public void handleZerotHeading(View view) {
    Toast.makeText(this, "handleZerotHeading", Toast.LENGTH_SHORT).show();
    mFieldOrientation.setCurrentFieldHeading(0);
  }

  public void handleGoOrMissionComplete(View view) {
    Toast.makeText(this, "handleGoOrMissionComplete", Toast.LENGTH_SHORT).show();
    if (mState == State.READY_FOR_MISSION) {
      mMatchStartTime = System.currentTimeMillis();
      mGoOrMissionCompleteButton.setBackgroundResource(R.drawable.red_button);
      mGoOrMissionCompleteButton.setText("Mission Complete!");
      setState(State.NEAR_BALL_SCRIPT);
    } else {
      setState(State.READY_FOR_MISSION);
    }
  }
}
