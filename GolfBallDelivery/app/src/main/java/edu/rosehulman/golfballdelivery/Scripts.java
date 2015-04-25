package edu.rosehulman.golfballdelivery;

import android.os.Handler;
import android.widget.Toast;

import edu.rosehulman.golfballdelivery.GolfBallDeliveryActivity.BallColor;
import edu.rosehulman.golfballdelivery.GolfBallDeliveryActivity.State;

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
    // TODO: Implement
  }

  
  /** Script to drop off the far ball. */
  public void farBallScript() {
    // TODO: Implement
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
    // TODO: Implement
  }

  // -------------------------------- Arm script(s) ----------------------------------------

  /** Removes a ball from the golf ball stand. */
  public void removeBallAtLocation(final int location) {
    // Remove a ball.
    // TODO: Replace with a script that might actually remove a ball. :)
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
