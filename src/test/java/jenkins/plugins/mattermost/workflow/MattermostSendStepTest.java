package jenkins.plugins.mattermost.workflow;

import hudson.model.TaskListener;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import jenkins.plugins.mattermost.MattermostNotifier;
import jenkins.plugins.mattermost.MattermostService;
import jenkins.security.ConfidentialStore;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.PrintStream;
import java.io.PrintWriter;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Traditional Unit tests, allows testing null Jenkins.get() */
@RunWith(MockitoJUnitRunner.Silent.class)
public class MattermostSendStepTest {

  @Mock TaskListener taskListenerMock;
  @Mock PrintStream printStreamMock;
  @Mock PrintWriter printWriterMock;
  @Mock StepContext stepContextMock;
  @Mock MattermostService mattermostServiceMock;
  @Mock Jenkins jenkins;
  @Mock MattermostNotifier.DescriptorImpl mattermostDescMock;

  private MattermostSendStep.MattermostSendStepExecution stepExecution;
  private MattermostSendStep mattermostSendStep;

  /** Static mocks are scoped, so they are opened in {@link #setUp} and closed in {@link #tearDown}. */
  private MockedStatic<Jenkins> jenkinsStatic;
  private MockedStatic<ConfidentialStore> confidentialStoreStatic;

  @Before
  public void setUp() {
    jenkinsStatic = mockStatic(Jenkins.class);
    when(jenkins.getDescriptor("mattermostNotifier")).thenReturn(mattermostDescMock);
    mattermostSendStep = new MattermostSendStep("message");
    stepExecution =
        spy(new MattermostSendStep.MattermostSendStepExecution(stepContextMock, mattermostSendStep));
    stepExecution.step = mattermostSendStep;

    jenkinsStatic.when(Jenkins::get).thenReturn(jenkins);
    jenkinsStatic.when(Jenkins::getInstanceOrNull).thenReturn(jenkins);

    // Secret.fromString() needs a ConfidentialStore; give it one that yields deterministic keys.
    confidentialStoreStatic = mockStatic(ConfidentialStore.class);
    ConfidentialStore csMock = mock(ConfidentialStore.class);
    confidentialStoreStatic.when(ConfidentialStore::get).thenReturn(csMock);
    when(csMock.randomBytes(anyInt())).thenAnswer(it -> new byte[(Integer) it.getArguments()[0]]);
  }

  @After
  public void tearDown() {
    confidentialStoreStatic.close();
    jenkinsStatic.close();
  }

  @Test
  public void testStepOverrides() throws Exception {
    mattermostSendStep.setIcon("icon");
    mattermostSendStep.setEndpoint("endpoint");
    mattermostSendStep.setChannel("channel");
    mattermostSendStep.setColor("good");

    stepExecution.listener = taskListenerMock;

    when(mattermostDescMock.getIcon()).thenReturn("differentIcon");

    when(taskListenerMock.getLogger()).thenReturn(printStreamMock);
    doNothing().when(printStreamMock).println();

    doReturn(mattermostServiceMock)
        .when(stepExecution)
        .getMattermostService(anyString(), anyString(), anyString());
    when(mattermostServiceMock.publish(anyString(), anyString())).thenReturn(true);

    stepExecution.run();
    verify(stepExecution, times(1)).getMattermostService("endpoint", "channel", "icon");
    verify(mattermostServiceMock, times(1)).publish("message", "", "good");
    assertFalse(stepExecution.step.isFailOnError());
  }

  @Test
  public void testValuesForGlobalConfig() throws Exception {

    stepExecution.listener = taskListenerMock;

    Secret encryptedEndpoint = Secret.fromString("globalEndpoint");
    when(mattermostDescMock.getEndpoint()).thenReturn(encryptedEndpoint);
    when(mattermostDescMock.getIcon()).thenReturn("globalIcon");
    when(mattermostDescMock.getRoom()).thenReturn("globalChannel");

    when(taskListenerMock.getLogger()).thenReturn(printStreamMock);
    doNothing().when(printStreamMock).println();

    doReturn(mattermostServiceMock)
        .when(stepExecution)
        .getMattermostService(anyString(), anyString(), anyString());

    stepExecution.run();
    verify(stepExecution, times(1))
        .getMattermostService("globalEndpoint", "globalChannel", "globalIcon");
    verify(mattermostServiceMock, times(1)).publish("message", "", "");
    assertNull(stepExecution.step.getEndpoint());
    assertNull(stepExecution.step.getIcon());
    assertNull(stepExecution.step.getChannel());
    assertNull(stepExecution.step.getColor());
    assertNull(stepExecution.step.getText());
  }

  @Test
  public void testNonNullEmptyColor() throws Exception {
    Secret encryptedEndpoint = Secret.fromString("globalEndpoint");
    when(mattermostDescMock.getEndpoint()).thenReturn(encryptedEndpoint);
    mattermostSendStep.setColor("");
    mattermostSendStep.setChannel("channel");
    mattermostSendStep.setIcon("");

    stepExecution.listener = taskListenerMock;

    when(taskListenerMock.getLogger()).thenReturn(printStreamMock);
    doNothing().when(printStreamMock).println();

    doReturn(mattermostServiceMock)
        .when(stepExecution)
        .getMattermostService(anyString(), anyString(), anyString());

    stepExecution.run();
    verify(mattermostServiceMock, times(1)).publish("message", "", "");
    assertNull(stepExecution.step.getColor());
  }

  @Test
  public void testNonNullPretext() throws Exception {
    TestListener target = new TestListener(8080);
    Thread thread = new Thread(target);
    thread.start();
    Secret encryptedEndpoint = Secret.fromString("http://localhost:8080");
    when(mattermostDescMock.getEndpoint()).thenReturn(encryptedEndpoint);
    mattermostSendStep.setText("@foo @bar");
    mattermostSendStep.setChannel("channel");
    stepExecution.listener = taskListenerMock;

    doReturn(mattermostServiceMock)
        .when(stepExecution)
        .getMattermostService(anyString(), anyString(), anyString());

    when(taskListenerMock.getLogger()).thenReturn(printStreamMock);
    doNothing().when(printStreamMock).println();

    stepExecution.run();
    verify(mattermostServiceMock, times(1)).publish("message", "@foo @bar", "");
  }

  @Test
  public void testNullJenkinsInstance() throws Exception {

    jenkinsStatic.when(Jenkins::getInstanceOrNull).thenReturn(null);

    stepExecution.listener = taskListenerMock;

    when(taskListenerMock.error(anyString())).thenReturn(printWriterMock);
    doNothing().when(printStreamMock).println();

    stepExecution.run();
    verify(taskListenerMock, times(1)).error(anyString());
  }
}
