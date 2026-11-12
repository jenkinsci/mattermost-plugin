package jenkins.plugins.mattermost.workflow;

import com.google.common.collect.ImmutableSet;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.AbortException;
import hudson.Extension;
import hudson.Util;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import jenkins.plugins.mattermost.MattermostNotifier;
import jenkins.plugins.mattermost.MattermostService;
import jenkins.plugins.mattermost.StandardMattermostService;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;
import java.util.Set;

/** Workflow step to send a Mattermost channel notification. */
public class MattermostSendStep extends Step
{

  private final @NonNull String message;
  private String text;
  private String color;
  private String channel;
  private String endpoint;
  private String icon;
  private boolean failOnError;

  @NonNull
  public String getMessage() {
    return message;
  }

  public String getText() {
    return text;
  }

  public String getColor() {
    return color;
  }

  @DataBoundSetter
  public void setText(String text) {
    this.text = Util.fixEmpty(text);
  }

  @DataBoundSetter
  public void setColor(String color) {
    this.color = Util.fixEmpty(color);
  }

  public String getChannel() {
    return channel;
  }

  @DataBoundSetter
  public void setChannel(String channel) {
    this.channel = Util.fixEmpty(channel);
  }

  public String getEndpoint() {
    return endpoint;
  }

  @DataBoundSetter
  public void setEndpoint(String endpoint) {
    this.endpoint = Util.fixEmpty(endpoint);
  }

  public String getIcon() {
    return icon;
  }

  @DataBoundSetter
  public void setIcon(String icon) {
    this.icon = Util.fixEmpty(icon);
  }

  public boolean isFailOnError() {
    return failOnError;
  }

  @DataBoundSetter
  public void setFailOnError(boolean failOnError) {
    this.failOnError = failOnError;
  }

  @DataBoundConstructor
  public MattermostSendStep(@NonNull String message) {
    this.message = message;
  }

	@Override
	public StepExecution start(StepContext context)
	{
		return new MattermostSendStepExecution(context, this);
	}

  @Extension
  public static class DescriptorImpl extends StepDescriptor
  {

    public DescriptorImpl() {
		super();
	}

	  @Override
	  public Set<? extends Class<?>> getRequiredContext()
	  {
		  return ImmutableSet.of(Run.class, TaskListener.class);
    }

    @Override
    public String getFunctionName() {
      return "mattermostSend";
    }

    @Override
    public String getDisplayName() {
      return "Send Mattermost message";
    }
  }

  public static class MattermostSendStepExecution
		  extends SynchronousNonBlockingStepExecution<Void>
  {

	  private static final long serialVersionUID = 1L;

	  transient MattermostSendStep step;

	  transient TaskListener listener;

    @SuppressFBWarnings("SE_TRANSIENT_FIELD_NOT_RESTORED")

	  protected MattermostSendStepExecution(StepContext context, MattermostSendStep mattermostSendStep)
	  {
		  super(context);
		  step = mattermostSendStep;
		  try
		  {
			  this.listener = this.getContext().get(TaskListener.class);
		  } catch (IOException | InterruptedException e)
		  {
        //TODO WARN REFACTOR
		  }
	  }

	  @Override
	  protected Void run() throws Exception
	  {

		  // default to global config values if not set in step, but allow step to
		  // override all global settings
		  Jenkins jenkins;
		  // Jenkins.get() throws when no instance is available.
		  try
		  {
			  jenkins = Jenkins.get();
		  } catch (IllegalStateException ne)
		  {
			  listener.error(String.format("Mattermost notification failed with exception: %s", ne), ne);
			  return null;
		  }//TODO REFACTOR jenkins.getdescriptor by class
		  MattermostNotifier.DescriptorImpl mattermostDesc =
				  jenkins.getDescriptorByType(MattermostNotifier.DescriptorImpl.class);
		  if (mattermostDesc == null)
			  mattermostDesc = (MattermostNotifier.DescriptorImpl) jenkins.getDescriptor("mattermostNotifier");//junit test fallback
		  String team =
          step.getEndpoint() != null
				  ? step.getEndpoint()
				  : mattermostDesc.getEndpoint().getPlainText();
		  String channel = step.channel != null ? step.channel : mattermostDesc.getRoom();
		  String icon = step.icon != null ? step.icon : (mattermostDesc.getIcon() != null ? mattermostDesc.getIcon() : "");
		  String color = step.color != null ? step.color : "";
		  String text = step.text != null ? step.text : "";

		  // placing in console log to simplify testing of retrieving values from global
		  // config or from step field; also used for tests
		  listener
          .getLogger()
          .printf(
              "Mattermost Send Pipeline step configured values from global config - connector: %s, icon: %s, channel: %s, color: %s",
              step.endpoint == null, step.icon == null, step.channel == null, step.color == null);

		  MattermostService mattermostService = getMattermostService(team, channel, icon);
		  boolean publishSuccess = mattermostService.publish(step.message, text, color);
		  if (!publishSuccess && step.failOnError)
		  {
			  throw new AbortException("Mattermost notification failed. See Jenkins logs for details.");
		  } else if (!publishSuccess)
		  {
			  listener.error("Mattermost notification failed. See Jenkins logs for details.");
		  }
		  return null;
	  }

	  // streamline unit testing
	  MattermostService getMattermostService(String team, String channel, String icon)
	  {
		  return new StandardMattermostService(team, channel, icon);
	  }
  }
}
