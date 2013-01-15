package controllers;

import play.*;
import play.mvc.*;
import play.data.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import com.twilio.sdk.TwilioRestClient;
import com.twilio.sdk.TwilioRestException;
import com.twilio.sdk.resource.list.*;
import com.twilio.sdk.resource.instance.*;
import com.twilio.sdk.resource.factory.*;
import views.html.*;

public class Conference extends Controller {

  /**
   * Returns an example number that represents the Merchant's non-Twilio number - presumably a mobile number from a database.
   * @return Phone number as a String
   */
  private static String getMerchantNumber() {
    return "+447777777777";
  }
  
  /**
   * Returns the Twilio Number that is advertised to dial through to a merchant.
   * @return Phone number as a String
   */
  private static String getProviderNumber() {
    return "+442033333333";
  }

  /**
   * Returns the Twilio Number that can be used by the robot. This allows us to have TwiML on both call legs.
   * @return Phone number as a String
   */
  private static String getBotNumber() {
    return "+442033222222";
  }


  /**
   * Returns the server name that the application is running on.
   * @return Server URL as a String
   */
  private static String getHost() {
    return "http://wxyz.localtunnel.com";
  }

  /**
   * Finds the name of a conference room given the merchant number and originating caller. Typically stored with the call data in a database.
   * @return A conference room name as a String
   */
  private static String getRoomId() {
    return "SomeConferenceRoomId";
  }

  /**
   * Twilio SID Value, here from the environment variables.
   */
  private static String getSid() {
	return System.getenv().get("TWILIO_SID");
  }
  
  /**
   * Twilio Key Value, here from the environment variables.
   */
  private static String getKey() {
	return System.getenv().get("TWILIO_KEY");
  }

  /**
   * Shorthand helper method to create a TwilioRestClient object.
   */
  private static TwilioRestClient getClient() {
    return new TwilioRestClient(getSid(),getKey());	
  }

  /**
   * Initial action for the incoming call. All calls to the getProviderNumber will be configured to this action.
   * It will initiate an outbound call to the merchant, and to the recording robot, then add the original caller into
   * a conference room. When the robot dials this number, we detect them by the From parameter, and just add the robot
   * to the conference room.
   */
  public static Result dialIn() {
    DynamicForm requestData = form().bindFromRequest();
    String from = requestData.get("From");
    
    //In the special case that the robot is dialing into the conference number, just connect them directly, don't make out going calls.
    if (from.equals(getBotNumber())) {
      return ok("<Response><Dial><Conference>"+getRoomId()+"</Conference></Dial></Response>").as("text/xml");
    }
        
    //Otherwise, we need to dial in the Merchant, Robot, and connect the call originator.
        
    //Dial in the merchant to the call...
    TwilioRestClient client = getClient();    
  	Account mainAccount = client.getAccount();
  	CallFactory callFactory = mainAccount.getCallFactory();

    Map<String,String> merchantCallParams = new HashMap<String,String>();

    merchantCallParams.put("To",getMerchantNumber());
    merchantCallParams.put("From",getProviderNumber());
    merchantCallParams.put("Url",getHost()+"/join");

    //Typically keep hold of this call handle to make changes and redirects later.
    try { callFactory.create(merchantCallParams); }
    catch (Exception e) { return ok("<Response><Say>We're sorry, something went wrong.</Say><Hangup/></Response>").as("text/xml"); }

    //Create the robot caller to monitor for DTMF tones.
    Map<String,String> botCallParams = new HashMap<String,String>();

    botCallParams.put("To",getProviderNumber());
    botCallParams.put("From",getBotNumber());
    botCallParams.put("Url",getHost()+"/botCycle");

    //Typically keep hold of this call handle to make changes and redirects later.
    try { callFactory.create(botCallParams); }
    catch (Exception e) { return ok("<Response><Say>We're sorry, something went wrong.</Say><Hangup/></Response>").as("text/xml"); }

    //Put the call originator into the conference while we contact the Merchant.
    return ok("<Response><Dial><Conference>"+getRoomId()+"</Conference></Dial></Response>").as("text/xml");
  }

  /**
   * Joins a merchant to the conference with originating dialer. 
   */  
  public static Result join() {
    return ok("<Response><Dial><Conference>"+getRoomId()+"</Conference></Dial></Response>").as("text/xml");
  }
  
  /**
   * Because we have a Twilio-to-Twilio call, we can use TwiML at both ends of the call. The receiving end is adding the robot
   * to the conference. But the calling end is running the Gather verb repeatedly (looping on timeout) until one of the call legs
   * presses a key. Then we evaluate it, and act. Here, we begin recording, but we could modify the call to start a redirect etc.
   */
  public static Result botCycle() {
    DynamicForm requestData = form().bindFromRequest();
    String digits = requestData.get("Digits");

    //Check to see if we need to make a recording, or redirect one leg of the call... Here we're just using the recording
    if ("*".equals(digits)) {
      return ok("<Response><Say>This call is now being recorded.</Say><Record action=\""+getHost()+"/record\"></Record></Response>").as("text/xml");
    }

    //If the gather has timed out (here we set to 1 minute to demonstrate) then recycle to keep trying to record.
    else return ok("<Response><Gather timeout=\"60\" numDigits=\"1\" action=\""+getHost()+"/botCycle\"></Gather></Response>").as("text/xml");
  }
  
  /**
   * At the end of the recording, Twilio will post the details of the WAV file, by appending .mp3 to this file name, we get that format.
   * We could return other information, or have the robot do other things on the call, but here we will just have the robot hangup.
   * In all probability, the recording stops after 5 seconds of silence from both parties because they have both disconnected. So the
   * robot is effectively talking to itself on an conference of 1!
   */ 
  public static Result record() {
    DynamicForm requestData = form().bindFromRequest();
    String recordingUrl = requestData.get("RecordingUrl");
    String recordingDuration = requestData.get("RecordingDuration");

    //We have a WAV and MP3 recording that we need to do something with:
    System.out.println("A Recording has been made at "+recordingUrl+" lasting "+recordingDuration+" seconds.");
    
    //We'll just have the Robot dial off at this point, but the most likely scenario is that both call participants have disconnected.
    return ok("<Response><Say>Recording Robot is disconnecting.</Say><Hangup/></Response>").as("text/xml");
  }
}