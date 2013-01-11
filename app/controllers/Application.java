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

public class Application extends Controller {

  private static String getNumber() { 
    return "+441536609344"; 
  }

  private static String getSid() {
	return System.getenv().get("TWILIO_SID");
  }
  
  private static String getKey() {
	return System.getenv().get("TWILIO_KEY");
  }

  private static TwilioRestClient getClient()
  {
    return new TwilioRestClient(getSid(),getKey());	
  }

  public static Result index() {
	DynamicForm requestData = form().bindFromRequest();
    String q = requestData.get("q");
    
    return ok(views.html.index.render(q));
  }

  public static Result sms() {
  	DynamicForm requestData = form().bindFromRequest();
    String body = requestData.get("Body");
    return ok("<Response><Sms>Hello: "+body+"</Sms></Response>").as("text/xml");
    
  }
  
  public static Result call() {
    return ok("<Response><Say>Hello</Say></Response>").as("text/xml");
  }

  public static Result messages() { 
    TwilioRestClient client = getClient();  
    
    Account account = client.getAccount();
    
    Map<String,String> filters = new HashMap<String,String>();
    filters.put("To",getNumber());
    
    List<Sms> messages = account.getSmsMessages(filters).getPageData();
    
    return ok(views.html.messages.render(messages));
  }

  public static Result twoStep() {
    TwilioRestClient client = getClient();  
    
    Account account = client.getAccount();
    
    Map<String,String> filters = new HashMap<String,String>();
    filters.put("To",getNumber());
    
    List<Sms> messages = account.getSmsMessages(filters).getPageData();
    
    List<String> numbers = new ArrayList<String>();
    
    for (Sms s : messages)
    {
      if (numbers.contains(s.getFrom())==false)
      {
        numbers.add(s.getFrom());
      }
    }
    
    CallFactory factory = account.getCallFactory();
    
    for (String n : numbers) {
      Map<String,String> settings = new HashMap<String,String>();
      settings.put("To",n);
      settings.put("From",getNumber());
      settings.put("Url","http://xxxx.localtunnel.com/code");

      try { factory.create(settings); }
      catch (Exception e) { return ok("BAD"); }
    }
    return ok("Dialing");
  }

  public static Result code()
  {
    return ok("<Response><Gather numDigits=\"4\" action=\"http://xxxx.localtunnel.com/valid\"><Say>Please enter the code!</Say></Gather></Response>").as("text/xml");
  }

  public static Result valid()
  {
    DynamicForm requestData = form().bindFromRequest();
    String digits = requestData.get("Digits");
    if (digits.equals("9876"))
    {
      return ok("<Response><Say>You code is correct!</Say><Hangup/></Response>").as("text/xml");
    }
    else return ok("<Response><Say language=\"es\">You got that wrong</Say></Response>").as("text/xml");
  }

}