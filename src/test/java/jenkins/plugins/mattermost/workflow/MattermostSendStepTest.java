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
import org.mockito.MockedStatic;

import java.io.PrintStream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.*;

public class MattermostSendStepTest {
  private final TaskListener listener = mock(TaskListener.class);
  private final MattermostService service = mock(MattermostService.class);
  private final Jenkins jenkins = mock(Jenkins.class);
  private final MattermostNotifier.DescriptorImpl descriptor =
      mock(MattermostNotifier.DescriptorImpl.class);
  private MockedStatic<Jenkins> jenkinsStatic;
  private MockedStatic<ConfidentialStore> confidentialStoreStatic;
  private MattermostSendStep step;
  private MattermostSendStep.MattermostSendStepExecution execution;

  @Before
  public void setUp() throws Exception {
    jenkinsStatic = mockStatic(Jenkins.class);
    jenkinsStatic.when(Jenkins::get).thenReturn(jenkins);
    when(jenkins.getDescriptorByType(MattermostNotifier.DescriptorImpl.class))
        .thenReturn(descriptor);

    confidentialStoreStatic = mockStatic(ConfidentialStore.class);
    ConfidentialStore store = mock(ConfidentialStore.class);
    confidentialStoreStatic.when(ConfidentialStore::get).thenReturn(store);
    when(store.randomBytes(anyInt())).thenAnswer(it -> new byte[it.getArgument(0)]);
    Secret endpoint = Secret.fromString("globalEndpoint");
    when(descriptor.getEndpoint()).thenReturn(endpoint);
    when(descriptor.getIcon()).thenReturn("globalIcon");
    when(descriptor.getRoom()).thenReturn("globalChannel");

    StepContext context = mock(StepContext.class);
    when(context.get(TaskListener.class)).thenReturn(listener);
    when(listener.getLogger()).thenReturn(mock(PrintStream.class));
    step = new MattermostSendStep("message");
    execution = spy(new MattermostSendStep.MattermostSendStepExecution(context, step));
    doReturn(service).when(execution).getMattermostService(anyString(), anyString(), anyString());
    when(service.publish(anyString(), anyString(), anyString())).thenReturn(true);
  }

  @After
  public void tearDown() {
    if (confidentialStoreStatic != null) confidentialStoreStatic.close();
    if (jenkinsStatic != null) jenkinsStatic.close();
  }

  @Test
  public void testStepOverrides() throws Exception {
    step.setIcon("icon");
    step.setEndpoint("endpoint");
    step.setChannel("channel");
    step.setColor("good");

    execution.run();

    verify(execution).getMattermostService("endpoint", "channel", "icon");
    verify(service).publish("message", "", "good");
    assertFalse(step.isFailOnError());
  }

  @Test
  public void testValuesForGlobalConfig() throws Exception {
    execution.run();

    verify(execution).getMattermostService("globalEndpoint", "globalChannel", "globalIcon");
    verify(service).publish("message", "", "");
    assertNull(step.getEndpoint());
    assertNull(step.getIcon());
    assertNull(step.getChannel());
    assertNull(step.getColor());
    assertNull(step.getText());
  }

  @Test
  public void testNonNullEmptyColor() throws Exception {
    step.setColor("");
    step.setChannel("channel");
    step.setIcon("");

    execution.run();

    verify(service).publish("message", "", "");
    assertNull(step.getColor());
  }

  @Test
  public void testNonNullPretext() throws Exception {
    step.setText("@foo @bar");
    step.setChannel("channel");

    execution.run();

    verify(service).publish("message", "@foo @bar", "");
  }

  @Test
  public void testNullJenkinsInstance() throws Exception {
    jenkinsStatic.when(Jenkins::get).thenThrow(IllegalStateException.class);

    execution.run();

    verify(listener).error(anyString(), any(Exception.class));
    verifyNoInteractions(service);
  }
}
