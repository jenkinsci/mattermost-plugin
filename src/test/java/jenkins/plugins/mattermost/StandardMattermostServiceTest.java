package jenkins.plugins.mattermost;

import jenkins.plugins.mattermost.workflow.TestListener;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Collections;

import static org.junit.Assert.*;

public class StandardMattermostServiceTest {
	@Rule
	public JenkinsRule jenkinsRule = new JenkinsRule();
	private TestListener target;
	private int port = 8088;

	@Before
	public void startHttp()
	{
		try
		{
			this.target = new TestListener(port);
			Thread thread = new Thread(target);
			thread.start();
		} catch (Exception ex)
		{
			port++;
			this.startHttp();
		}
	}
  /**
   * Publish should generally not rethrow exceptions, or it will cause a build job to fail at end.
   */
  @Test
  public void publishWithBadHostShouldNotRethrowExceptions() {
	  StandardMattermostService service = new StandardMattermostService("http://foo", "#general", "");
    service.setEndpoint("hostvaluethatwillcausepublishtofail");
    service.publish("message");
  }

  /** Use a valid host, but an invalid team domain */
  @Test
  public void invalidHostShouldFail() {
	  StandardMattermostService service = new StandardMattermostService("http://my", "#general", "");
    service.publish("message");
  }

  @Test
  public void successfulPublishToASingleRoomReturnsTrue() {
    StandardMattermostService service =
			new StandardMattermostService("http://localhost:" + port, "#room1", "");
    assertTrue(service.publish("message"));
  }

  @Test
  public void successfulPublishToMultipleRoomsReturnsTrue() {
    StandardMattermostService service =
			new StandardMattermostService("http://localhost:" + port, "#room1,#room2,#room3", "");

    assertTrue(service.publish("message"));
  }

  @Test
  public void failedPublishToASingleRoomReturnsFalse() {
    StandardMattermostService service =
			new StandardMattermostService("http://endpoint", "#room1", "");
    assertFalse(service.publish("message"));
  }

  @Test
  public void singleFailedPublishToMultipleRoomsReturnsFalse() {
    StandardMattermostService service =
			new StandardMattermostService("http://endpoint", "#room1,#room2,#room3", "");
    assertFalse(service.publish("message"));
  }

  @Test
  public void publishToEmptyRoomReturnsTrue() {
	  StandardMattermostService service = new StandardMattermostService("http://localhost:" + port, "", "");
    assertTrue(service.publish("message"));
  }

  @Test
  public void isProxyRequiredEmtyNoProxyHostsReturnsTrue() {
    StandardMattermostService service =
        new StandardMattermostService("http://mymattermost.endpoint.com", "roomid", "icon");
	  assertTrue(service.isProxyRequired(Collections.emptyList()));
  }

  @Test
  public void isProxyRequiredNoProxyHostsDoesNotMatchReturnsTrue() {
    StandardMattermostService service =
        new StandardMattermostService("http://mymattermost.endpoint.com", "roomid", "icon");
    assertTrue(
            service.isProxyRequired("*.internal.com"));
  }

  @Test
  public void isProxyRequiredNoProxyHostsMatchReturnsFalse() {
    StandardMattermostService service =
        new StandardMattermostService("http://mymattermost.endpoint.com", "roomid", "icon");
    assertFalse(
            service.isProxyRequired("*.endpoint.com"));
  }

  @Test
  public void isProxyRequiredInvalidEndPointReturnsTrue() {
    StandardMattermostService service =
			new StandardMattermostService("http://mymattermost.endpoint.com", "roomid", "icon");
    assertTrue(
            service.isProxyRequired("*.internal.com"));
  }
}
