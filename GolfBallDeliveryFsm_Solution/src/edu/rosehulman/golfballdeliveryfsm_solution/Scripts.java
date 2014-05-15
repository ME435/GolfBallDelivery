package edu.rosehulman.golfballdeliveryfsm_solution;

import android.os.Handler;
import android.widget.Toast;
import edu.rosehulman.golfballdeliveryfsm_solution.GolfBallDeliveryActivity.BallColor;
import edu.rosehulman.golfballdeliveryfsm_solution.GolfBallDeliveryActivity.State;
import edu.rosehulman.me435.NavUtils;
import edu.rosehulman.me435.RobotActivity;

public class Scripts {

  /** Reference to the primary activity. */
  private GolfBallDeliveryActivity mGolfBallDeliveryActivity;

  /** Handler used to create scripts. */
  protected Handler mCommandHandler = new Handler();

  /** Time in milliseconds needed to perform a ball removal. */
  private int ARM_REMOVAL_TIME_MS = 15000;

  /** Simple constructor. */
  public Scripts(GolfBallDeliveryActivity golfBallDeliveryActivity) {
    mGolfBallDeliveryActivity = golfBallDeliveryActivity;
  }

  /** Used to test your values for straight driving. */
  public void testStraightDrive() {
    Toast.makeText(mGolfBallDeliveryActivity, "Begin Short straight drive test at " +
        mGolfBallDeliveryActivity.mLeftStraightPwmValue + "  " + mGolfBallDeliveryActivity.mRightStraightPwmValue,
        Toast.LENGTH_SHORT).show();
    mGolfBallDeliveryActivity.sendWheelSpeed(mGolfBallDeliveryActivity.mLeftStraightPwmValue, mGolfBallDeliveryActivity.mRightStraightPwmValue);
    mCommandHandler.postDelayed(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(mGolfBallDeliveryActivity, "End Short straight drive test", Toast.LENGTH_SHORT).show();
        mGolfBallDeliveryActivity.sendWheelSpeed(0, 0);
      }
    }, 4000);
  }

  /** Runs the script to drive to the near ball (perfectly straight) and drop it off. */
  public void nearBallScript() {
    final double distanceToNearBall = NavUtils.getDistance(15, 0, 90, 50);
    final long driveTimeToNearBallMs = (long) (distanceToNearBall / RobotActivity.DEFAULT_SPEED_FT_PER_SEC * 1000);
    Toast.makeText(
        mGolfBallDeliveryActivity,
        "Near ball: Straight for " + driveTimeToNearBallMs / 1000 + "  Left = " + mGolfBallDeliveryActivity.mLeftStraightPwmValue + "  Right = "
            + mGolfBallDeliveryActivity.mRightStraightPwmValue, Toast.LENGTH_SHORT).show();
    mGolfBallDeliveryActivity.sendWheelSpeed(mGolfBallDeliveryActivity.mLeftStraightPwmValue, mGolfBallDeliveryActivity.mRightStraightPwmValue);
    mCommandHandler.postDelayed(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(mGolfBallDeliveryActivity, "near ball script 1", Toast.LENGTH_SHORT).show();
        mGolfBallDeliveryActivity.sendWheelSpeed(0, 0);
        // Figure out which ball to remove.
        for (int i = 0; i < 3; i++) {
          BallColor currentLocationsBallColor = mGolfBallDeliveryActivity.mLocationColors[i];
          if (mGolfBallDeliveryActivity.mOnRedTeam && (currentLocationsBallColor == BallColor.RED || currentLocationsBallColor == BallColor.GREEN) ||
              !mGolfBallDeliveryActivity.mOnRedTeam && (currentLocationsBallColor == BallColor.BLUE || currentLocationsBallColor == BallColor.YELLOW)) {
            removeBallAtLocation(i + 1);
            break;
          }
        }
      }
    }, driveTimeToNearBallMs);
    mCommandHandler.postDelayed(new Runnable() {
      @Override
      public void run() {
        // Start moving again and move on to the next state.
        mGolfBallDeliveryActivity.sendWheelSpeed(mGolfBallDeliveryActivity.mLeftStraightPwmValue, mGolfBallDeliveryActivity.mRightStraightPwmValue);
        Toast.makeText(mGolfBallDeliveryActivity, "near ball script 2", Toast.LENGTH_SHORT).show();
        mGolfBallDeliveryActivity.setState(State.DRIVE_TOWARDS_FAR_BALL);
      }
    }, driveTimeToNearBallMs + ARM_REMOVAL_TIME_MS);
  }

  
  /** Script to drop off the far ball. */
  public void farBallScript() {
    mGolfBallDeliveryActivity.sendWheelSpeed(0, 0);
    // Figure out which ball to remove.
    for (int i = 0; i < 3; i++) {
      BallColor currentLocationsBallColor = mGolfBallDeliveryActivity.mLocationColors[i];
      if (!mGolfBallDeliveryActivity.mOnRedTeam && (currentLocationsBallColor == BallColor.RED ||
                                                    currentLocationsBallColor == BallColor.GREEN) ||
          mGolfBallDeliveryActivity.mOnRedTeam &&  (currentLocationsBallColor == BallColor.BLUE ||
                                                    currentLocationsBallColor == BallColor.YELLOW)) {
        removeBallAtLocation(i + 1);
        break;
      }
    }
    mCommandHandler.postDelayed(new Runnable() {
      @Override
      public void run() {
        // Flip it. Note, there are WAY better solutions to flipping a 180 using the FieldOrientation sensor.
        // For now we'll just hardcode a time and PWM values to make a CCW 180 degree turn (testing required).
        mGolfBallDeliveryActivity.sendWheelSpeed(50, 255);
      }
    }, ARM_REMOVAL_TIME_MS);
    mCommandHandler.postDelayed(new Runnable() {
      @Override
      public void run() {
        // Start moving again in a straight line to fix the heading before moving on to the next state.
        mGolfBallDeliveryActivity.sendWheelSpeed(mGolfBallDeliveryActivity.mLeftStraightPwmValue,
                                                 mGolfBallDeliveryActivity.mRightStraightPwmValue);
      }
    }, ARM_REMOVAL_TIME_MS + 2000);
    mCommandHandler.postDelayed(new Runnable() {
      @Override
      public void run() {
        mGolfBallDeliveryActivity.setState(State.DRIVE_TOWARDS_HOME);
      }
    }, ARM_REMOVAL_TIME_MS + 8000);
  }

  // -------------------------------- Arc driving ----------------------------------------

  /** Sends commands to the Wild Thumper to actually drive the arc that was
   * calculated from NavUtils. Converts the turn radius and arc length into a
   * wheel speed command and a stop time.
   * 
   * @param turnRadiusFt Radius of the pretend circle to drive along. Negative
   * values indicate a turn to the left. Positive values indicate a turn to the
   * right.
   * @param arcLengthFt Length of the arc to drive. */
  public void driveArc(double turnRadiusFt, double arcLengthFt, final State nextStateAfterArcCompletes) {
    int leftDutyCycle = 255, rightDutyCycle = 255; // One side or the other will be at 255.

    // Use the experimental data from Lab 7 to calculate the speed and stop  time.
    if (turnRadiusFt < 0) {
      // Turn left
      turnRadiusFt = Math.abs(turnRadiusFt);
      leftDutyCycle = (int) (-0.00004 * Math.pow(turnRadiusFt, 4) + 0.0076 * Math.pow(turnRadiusFt, 3) - 0.5034
          * Math.pow(turnRadiusFt, 2) + 16.051 * turnRadiusFt);
    } else {
      // Turn right
      rightDutyCycle = (int) (-0.0002 * Math.pow(turnRadiusFt, 4) + 0.0206 * Math.pow(turnRadiusFt, 3) - 0.9359
          * Math.pow(turnRadiusFt, 2) + 21.819 * turnRadiusFt + 9.2765);
    }
    // Constraint result.
    leftDutyCycle = Math.min(leftDutyCycle, 255);
    leftDutyCycle = Math.max(leftDutyCycle, 0);
    rightDutyCycle = Math.min(rightDutyCycle, 255);
    rightDutyCycle = Math.max(rightDutyCycle, 0);

    // Start driving at the calculated PWM speeds.
    mGolfBallDeliveryActivity.sendWheelSpeed(leftDutyCycle, rightDutyCycle);

    // Figure out the time needed to drive
    long stopTime = (long) (arcLengthFt / RobotActivity.DEFAULT_SPEED_FT_PER_SEC * 1000.0);
    mCommandHandler.postDelayed(new Runnable() {
      @Override
      public void run() {
        mGolfBallDeliveryActivity.sendWheelSpeed(0, 0);
        mGolfBallDeliveryActivity.setState(nextStateAfterArcCompletes);
      }
    }, stopTime);
  }

  // -------------------------------- Arm script(s) ----------------------------------------

  /** Removes a ball from the golf ball stand. */
  public void removeBallAtLocation(final int location) {
    // Remove a ball.
    final String positionHomeSide = "POSITION 83 90 0 -90 90";
    final String positionSideUp = "POSITION 90 141 -60 -180 169";

    final String positionStand1Up = "POSITION 48 141 -60 -180 169";
    final String positionStand1 = "POSITION 48 141 -82 -180 169";

    final String positionStand2Up = "POSITION 24 141 -60 -180 169";
    final String positionStand2 = "POSITION 24 151 -90 -180 174";

    final String positionStand3Up = "POSITION -3 141 -60 -180 169";
    final String positionStand3 = "POSITION -8 148 -88 -180 173";

    final String positionDrop = "POSITION 90 56 -50 -103 169";
    final String positionHome = "POSITION 0 90 0 -90 90";
    final String gripperOpen = "GRIPPER 45";
    final String gripperClose = "GRIPPER 0";

    mGolfBallDeliveryActivity.sendArmCommand("ATTACH 111111"); // Just in case
    mCommandHandler.postDelayed(new Runnable() {
      @Override
      public void run() {
        mGolfBallDeliveryActivity.sendArmCommand(positionHomeSide);
      }
    }, 10);
    mCommandHandler.postDelayed(new Runnable() {
      @Override
      public void run() {
        mGolfBallDeliveryActivity.sendArmCommand(positionSideUp);
      }
    }, 2000);
    mCommandHandler.postDelayed(new Runnable() {
      @Override
      public void run() {
        mGolfBallDeliveryActivity.sendArmCommand(gripperOpen);
        switch (location) {
        case 1:
          mGolfBallDeliveryActivity.sendArmCommand(positionStand1Up);
          break;
        case 2:
          mGolfBallDeliveryActivity.sendArmCommand(positionStand2Up);
          break;
        case 3:
          mGolfBallDeliveryActivity.sendArmCommand(positionStand3Up);
          break;
        }
      }
    }, 4000);
    mCommandHandler.postDelayed(new Runnable() {
      @Override
      public void run() {
        switch (location) {
        case 1:
          mGolfBallDeliveryActivity.sendArmCommand(positionStand1);
          break;
        case 2:
          mGolfBallDeliveryActivity.sendArmCommand(positionStand2);
          break;
        case 3:
          mGolfBallDeliveryActivity.sendArmCommand(positionStand3);
          break;
        }
      }
    }, 6000);
    mCommandHandler.postDelayed(new Runnable() {
      @Override
      public void run() {
        mGolfBallDeliveryActivity.sendArmCommand(gripperClose);
      }
    }, 7000);
    mCommandHandler.postDelayed(new Runnable() {
      @Override
      public void run() {
        switch (location) {
        case 1:
          mGolfBallDeliveryActivity.sendArmCommand(positionStand1Up);
          break;
        case 2:
          mGolfBallDeliveryActivity.sendArmCommand(positionStand2Up);
          break;
        case 3:
          mGolfBallDeliveryActivity.sendArmCommand(positionStand3Up);
          break;
        }
      }
    }, 9000);
    mCommandHandler.postDelayed(new Runnable() {
      @Override
      public void run() {
        mGolfBallDeliveryActivity.sendArmCommand(positionDrop);
      }
    }, 11000);
    mCommandHandler.postDelayed(new Runnable() {
      @Override
      public void run() {
        mGolfBallDeliveryActivity.sendArmCommand(gripperOpen);
      }
    }, 13000);
    mCommandHandler.postDelayed(new Runnable() {
      @Override
      public void run() {
        mGolfBallDeliveryActivity.sendArmCommand(positionHome);
      }
    }, 14000);
    mCommandHandler.postDelayed(new Runnable() {
      @Override
      public void run() {
        mGolfBallDeliveryActivity.setLocationToColor(location, BallColor.NONE);
      }
    }, ARM_REMOVAL_TIME_MS);
  }
}
