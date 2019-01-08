package jenkins.plugins.mattermost;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.listeners.ItemListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;

import static hudson.Util.fixNull;


public class MattermostNotifier extends Notifier {

	private static final Logger logger = Logger.getLogger(MattermostNotifier.class.getName());

	private String endpoint;
	private String buildServerUrl;
	private String room;
	private String icon;
	private String sendAs;
	private boolean startNotification;
	private boolean notifySuccess;
	private boolean notifyAborted;
	private boolean notifyNotBuilt;
	private boolean notifyUnstable;
	private boolean notifyFailure;
	private boolean notifyBackToNormal;
	private boolean notifyRepeatedFailure;
	private boolean includeTestSummary;
	transient private boolean showCommitList;
	private CommitInfoChoice commitInfoChoice;
	private boolean includeCustomAttachmentMessage;
	private String customAttachmentMessage;
	private boolean includeCustomMessage;
	private String customMessage;

	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl)super.getDescriptor();
	}

	public String getEndpoint() {
		return endpoint;
	}

	public String getRoom() {
		return room;
	}

	public String getIcon() {
		return icon;
	}

	public String getBuildServerUrl() {
		if (buildServerUrl == null || buildServerUrl.equals("")) {
			// Note: current code should no longer use "new JenkinsLocationConfiguration()"
			// as only one instance per runtime is really supported by the current core.
			JenkinsLocationConfiguration jenkinsConfig = JenkinsLocationConfiguration.get();
			return (jenkinsConfig == null) ? null : jenkinsConfig.getUrl();
		} else {
			return buildServerUrl;
		}
	}

	public String getSendAs() {
		return sendAs;
	}

	public boolean getStartNotification() {
		return startNotification;
	}

	public boolean getNotifySuccess() {
		return notifySuccess;
	}

	public CommitInfoChoice getCommitInfoChoice() {
		return commitInfoChoice;
	}

	public boolean getNotifyAborted() {
		return notifyAborted;
	}

	public boolean getNotifyFailure() {
		return notifyFailure;
	}

	public boolean getNotifyNotBuilt() {
		return notifyNotBuilt;
	}

	public boolean getNotifyUnstable() {
		return notifyUnstable;
	}

	public boolean getNotifyBackToNormal() {
		return notifyBackToNormal;
	}

	public boolean getIncludeTestSummary() {
		return includeTestSummary;
	}

	public boolean getNotifyRepeatedFailure() {
		return notifyRepeatedFailure;
	}

	public boolean getIncludeCustomAttachmentMessage() {
		return includeCustomAttachmentMessage;
	}

	public String getCustomAttachmentMessage() {
		return customAttachmentMessage;
	}

	public boolean getIncludeCustomMessage() {
		return includeCustomMessage;
	}

	public String getCustomMessage() {
		return customMessage;
	}

	public void setEndpoint(@CheckForNull String endpoint) {
		this.endpoint = fixNull(endpoint);
	}

	@DataBoundSetter
	public void setRoom(@CheckForNull String room) {
		this.room = fixNull(room);
	}

	@DataBoundSetter
	public void setIcon(@CheckForNull String icon) {
		this.icon = fixNull(icon);
	}

	@DataBoundSetter
	public void setBuildServerUrl(@CheckForNull String buildServerUrl) {
		this.buildServerUrl = fixNull(buildServerUrl);
	}

	@DataBoundSetter
	public void setSendAs(@CheckForNull String sendAs) {
		this.sendAs = fixNull(sendAs);
	}

	@DataBoundSetter
	public void setStartNotification(boolean startNotification) {
		this.startNotification = startNotification;
	}

	@DataBoundSetter
	public void setNotifySuccess(boolean notifySuccess) {
		this.notifySuccess = notifySuccess;
	}

	@DataBoundSetter
	public void setCommitInfoChoice(CommitInfoChoice commitInfoChoice) {
		this.commitInfoChoice = commitInfoChoice;
	}

	@DataBoundSetter
	public void setNotifyAborted(boolean notifyAborted) {
		this.notifyAborted = notifyAborted;
	}

	@DataBoundSetter
	public void setNotifyFailure(boolean notifyFailure) {
		this.notifyFailure = notifyFailure;
	}

	@DataBoundSetter
	public void setNotifyNotBuilt(boolean notifyNotBuilt) {
		this.notifyNotBuilt = notifyNotBuilt;
	}

	@DataBoundSetter
	public void setNotifyUnstable(boolean notifyUnstable) {
		this.notifyUnstable = notifyUnstable;
	}

	@DataBoundSetter
	public void setNotifyBackToNormal(boolean notifyBackToNormal) {
		this.notifyBackToNormal = notifyBackToNormal;
	}

	@DataBoundSetter
	public void setIncludeTestSummary(boolean includeTestSummary) {
		this.includeTestSummary = includeTestSummary;
	}

	@DataBoundSetter
	public void setNotifyRepeatedFailure(boolean notifyRepeatedFailure) {
		this.notifyRepeatedFailure = notifyRepeatedFailure;
	}

	@DataBoundSetter
	public void setIncludeCustomAttachmentMessage(boolean includeCustomAttachmentMessage) {
		this.includeCustomAttachmentMessage = includeCustomAttachmentMessage;
	}

	@DataBoundSetter
	public void setCustomAttachmentMessage(@CheckForNull String customAttachmentMessage) {
		this.customAttachmentMessage = fixNull(customAttachmentMessage);
	}

	@DataBoundSetter
	public void setIncludeCustomMessage(boolean includeCustomMessage) {
		this.includeCustomMessage = includeCustomMessage;
	}

	@DataBoundSetter
	public void setCustomMessage(@CheckForNull String customMessage) {
		this.customMessage = fixNull(customMessage);
	}

	@DataBoundConstructor
	public MattermostNotifier(final String endpoint, final String room, final String icon, final String buildServerUrl,
			final String sendAs, final boolean startNotification, final boolean notifyAborted, final boolean notifyFailure,
			final boolean notifyNotBuilt, final boolean notifySuccess, final boolean notifyUnstable, final boolean notifyBackToNormal,
			final boolean notifyRepeatedFailure, final boolean includeTestSummary, CommitInfoChoice commitInfoChoice,
			boolean includeCustomAttachmentMessage, String customAttachmentMessage, final boolean includeCustomMessage, final String customMessage) {
		super();
		this.endpoint = endpoint;
		this.buildServerUrl = buildServerUrl;
		this.room = room;
		this.icon = icon;
		this.sendAs = sendAs;
		this.startNotification = startNotification;
		this.notifyAborted = notifyAborted;
		this.notifyFailure = notifyFailure;
		this.notifyNotBuilt = notifyNotBuilt;
		this.notifySuccess = notifySuccess;
		this.notifyUnstable = notifyUnstable;
		this.notifyBackToNormal = notifyBackToNormal;
		this.notifyRepeatedFailure = notifyRepeatedFailure;
		this.includeTestSummary = includeTestSummary;
		this.commitInfoChoice = commitInfoChoice;
		this.includeCustomAttachmentMessage = includeCustomAttachmentMessage;
		if(includeCustomAttachmentMessage) {
			this.customAttachmentMessage = customAttachmentMessage;
		} else {
			this.customAttachmentMessage = null;
		}

		this.includeCustomMessage = includeCustomMessage;
		if(includeCustomMessage) {
			this.customMessage = customMessage;
		} else {
			this.customMessage = null;
		}
	}

	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

	public MattermostService newMattermostService(AbstractBuild r, BuildListener listener) {
		String endpoint = this.endpoint;
		if (StringUtils.isEmpty(endpoint)) {
			endpoint = getDescriptor().getEndpoint();
		}
		String room = this.room;
		if (StringUtils.isEmpty(room)) {
			room = getDescriptor().getRoom();
		}

		String icon = this.icon;
		if (StringUtils.isEmpty(icon)) {
			icon = getDescriptor().getIcon();
		}

		EnvVars env = null;
		try {
			env = r.getEnvironment(listener);
		} catch (Exception e) {
			listener.getLogger().println("Error retrieving environment vars: " + e.getMessage());
			env = new EnvVars();
		}
		endpoint = env.expand(endpoint);
		room = env.expand(room);
		icon = env.expand(icon);

		return new StandardMattermostService(endpoint, room, icon);
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
		return true;
	}

	@Override
	public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
		if (startNotification) {
			Map<Descriptor<Publisher>, Publisher> map = build.getProject().getPublishersList().toMap();
			for (Publisher publisher : map.values()) {
				if (publisher instanceof MattermostNotifier) {
					logger.info("Invoking Started...");
					new ActiveNotifier((MattermostNotifier)publisher, listener).started(build);
				}
			}
		}
		return super.prebuild(build, listener);
	}

	@Extension @Symbol("mattermostNotifier")
	public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {

		private String endpoint;
		private String room;
		private String icon;
		private String buildServerUrl;
		private String sendAs;
		
		public DescriptorImpl() {
			load();
		}

		@DataBoundSetter
		public void setEndpoint(String endpoint) {
			this.endpoint = endpoint;
		}
		
		public String getEndpoint() {
			return endpoint;
		}

		@DataBoundSetter
		public void setRoom(String room) {
			this.room = room;
		}
		
		public String getRoom() {
			return room;
		}

		@DataBoundSetter
		public void setIcon(String icon) {
			this.icon = icon;
		}
		
		public String getIcon() {
			return icon;
		}

		@DataBoundSetter
		public void setBuildServerUrl(String buildServerUrl) {
			this.buildServerUrl = buildServerUrl;
		}
		
		public String getBuildServerUrl() {
			if (buildServerUrl == null || buildServerUrl.equals("")) {
				// Note: current code should no longer use "new JenkinsLocationConfiguration()"
				// as only one instance per runtime is really supported by the current core.
				JenkinsLocationConfiguration jenkinsConfig = JenkinsLocationConfiguration.get();
				return (jenkinsConfig == null) ? null : jenkinsConfig.getUrl();
			} else {
				return buildServerUrl;
			}
		}

		public String getSendAs() {
			return sendAs;
		}

		public ListBoxModel doFillCommitInfoChoiceItems() {
			ListBoxModel model = new ListBoxModel();
      for (CommitInfoChoice choice : CommitInfoChoice.values()) {
				model.add(choice.getDisplayName(), choice.name());
			}
			return model;
		}

		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
			return true;
		}

		@Override
		public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
			req.bindJSON(this, formData);
      save();
      return true;
		}

		MattermostService getMattermostService(final String endpoint, final String room, final String icon) {
			return new StandardMattermostService(endpoint, room, icon);
		}

		@Override
		public String getDisplayName() {
			return "Mattermost Notifications";
		}

		public FormValidation doTestConnection(@QueryParameter("endpoint") final String endpoint,
				@QueryParameter("room") final String room,
				@QueryParameter("icon") final String icon,
				@QueryParameter("buildServerUrl") final String buildServerUrl) throws FormException {
			try {
				String targetEndpoint = endpoint;
				if (StringUtils.isEmpty(targetEndpoint)) {
					targetEndpoint = this.endpoint;
				}
				String targetRoom = room;
				if (StringUtils.isEmpty(targetRoom)) {
					targetRoom = this.room;
				}
				String targetIcon = icon;
				if (StringUtils.isEmpty(targetIcon)) {
					targetIcon = this.icon;
				}
				String targetBuildServerUrl = buildServerUrl;
				if (StringUtils.isEmpty(targetBuildServerUrl)) {
					targetBuildServerUrl = this.buildServerUrl;
				}
				MattermostService testMattermostService = getMattermostService(targetEndpoint, targetRoom, targetIcon);
				String message = "Mattermost/Jenkins plugin: you're all set! (parameters: " +
						"endpoint='" + targetEndpoint + "', " +
						"room='" + targetRoom + "', " +
						"icon='" + targetIcon + "', " +
						"buildServerUrl='" + targetBuildServerUrl + "'" +
						")";
				boolean success = testMattermostService.publish(message, "good");
				return success ? FormValidation.ok("Success") : FormValidation.error("Failure");
			} catch (Exception e) {
				return FormValidation.error("Client error : " + e.getMessage());
			}
		}
	}


	@Deprecated
	public static class MattermostJobProperty extends hudson.model.JobProperty<AbstractProject<?, ?>> {

		private String endpoint;
		private String room;
		private String icon;
		private boolean startNotification;
		private boolean notifySuccess;
		private boolean notifyAborted;
		private boolean notifyNotBuilt;
		private boolean notifyUnstable;
		private boolean notifyFailure;
		private boolean notifyBackToNormal;
		private boolean notifyRepeatedFailure;
		private boolean includeTestSummary;
		private boolean showCommitList;
		private boolean includeCustomAttachmentMessage;
		private String customAttachmentMessage;
		private String customMessage;
		private boolean includeCustomMessage;

		@DataBoundConstructor
		public MattermostJobProperty(String teamDomain,
				String room,
				String icon,
				boolean startNotification,
				boolean notifyAborted,
				boolean notifyFailure,
				boolean notifyNotBuilt,
				boolean notifySuccess,
				boolean notifyUnstable,
				boolean notifyBackToNormal,
				boolean notifyRepeatedFailure,
				boolean includeTestSummary,
				boolean showCommitList,
				boolean includeCustomAttachmentMessage,
				String customAttachmentMessage,
				boolean includeCustomMessage,
				String customMessage) {
			this.endpoint = teamDomain;
			this.room = room;
			this.icon = icon;
			this.startNotification = startNotification;
			this.notifyAborted = notifyAborted;
			this.notifyFailure = notifyFailure;
			this.notifyNotBuilt = notifyNotBuilt;
			this.notifySuccess = notifySuccess;
			this.notifyUnstable = notifyUnstable;
			this.notifyBackToNormal = notifyBackToNormal;
			this.notifyRepeatedFailure = notifyRepeatedFailure;
			this.includeTestSummary = includeTestSummary;
			this.showCommitList = showCommitList;
			this.includeCustomAttachmentMessage = includeCustomAttachmentMessage;
			this.customAttachmentMessage = customAttachmentMessage;
			this.includeCustomMessage = includeCustomMessage;
			this.customMessage = customMessage;
		}

		@Exported
		public String getEndpoint() {
			return endpoint;
		}

		@Exported
		public String getIcon() {
			return icon;
		}

		@Exported
		public String getRoom() {
			return room;
		}

		@Exported
		public boolean getStartNotification() {
			return startNotification;
		}

		@Exported
		public boolean getNotifySuccess() {
			return notifySuccess;
		}

		@Exported
		public boolean getShowCommitList() {
			return showCommitList;
		}

		@Override
		public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
			return super.prebuild(build, listener);
		}

		@Exported
		public boolean getNotifyAborted() {
			return notifyAborted;
		}

		@Exported
		public boolean getNotifyFailure() {
			return notifyFailure;
		}

		@Exported
		public boolean getNotifyNotBuilt() {
			return notifyNotBuilt;
		}

		@Exported
		public boolean getNotifyUnstable() {
			return notifyUnstable;
		}

		@Exported
		public boolean getNotifyBackToNormal() {
			return notifyBackToNormal;
		}

		@Exported
		public boolean includeTestSummary() {
			return includeTestSummary;
		}

		@Exported
		public boolean getNotifyRepeatedFailure() {
			return notifyRepeatedFailure;
		}

		@Exported
		public boolean includeCustomAttachmentMessage() {
			return includeCustomAttachmentMessage;
		}

		@Exported
		public String getCustomAttachmentMessage() {
			return customAttachmentMessage;
		}

		@Exported
		public boolean includeCustomMessage() {
			return includeCustomMessage;
		}

		@Exported
		public String getCustomMessage() {
			return customMessage;
		}
	}


	@Extension
	public static final class Migrator extends ItemListener {

		@SuppressWarnings("deprecation")
		@Override
		public void onLoaded() {
			logger.info("Starting Settings Migration Process");
			for (AbstractProject<?, ?> p : Jenkins.getInstance().getAllItems(AbstractProject.class)) {
				final MattermostJobProperty mattermostJobProperty = p.getProperty(MattermostJobProperty.class);

				if (mattermostJobProperty == null) {
					logger.fine(String
							.format("Configuration is already up to date for \"%s\", skipping migration",
									p.getName()));
					continue;
				}

				MattermostNotifier mattermostNotifier = p.getPublishersList().get(MattermostNotifier.class);

				if (mattermostNotifier == null) {
					logger.fine(String
							.format("Configuration does not have a notifier for \"%s\", not migrating settings",
									p.getName()));
				} else {
					logger.info(String.format("Starting migration for \"%s\"", p.getName()));
					//map settings
					if (StringUtils.isBlank(mattermostNotifier.endpoint)) {
						mattermostNotifier.endpoint = mattermostJobProperty.getEndpoint();
					}
					if (StringUtils.isBlank(mattermostNotifier.icon)) {
						mattermostNotifier.icon = mattermostJobProperty.getIcon();
					}
					if (StringUtils.isBlank(mattermostNotifier.room)) {
						mattermostNotifier.room = mattermostJobProperty.getRoom();
					}

					mattermostNotifier.startNotification = mattermostJobProperty.getStartNotification();

					mattermostNotifier.notifyAborted = mattermostJobProperty.getNotifyAborted();
					mattermostNotifier.notifyFailure = mattermostJobProperty.getNotifyFailure();
					mattermostNotifier.notifyNotBuilt = mattermostJobProperty.getNotifyNotBuilt();
					mattermostNotifier.notifySuccess = mattermostJobProperty.getNotifySuccess();
					mattermostNotifier.notifyUnstable = mattermostJobProperty.getNotifyUnstable();
					mattermostNotifier.notifyBackToNormal = mattermostJobProperty.getNotifyBackToNormal();
					mattermostNotifier.notifyRepeatedFailure = mattermostJobProperty.getNotifyRepeatedFailure();

					mattermostNotifier.includeTestSummary = mattermostJobProperty.includeTestSummary();
					mattermostNotifier.commitInfoChoice = mattermostJobProperty.getShowCommitList() ? CommitInfoChoice.AUTHORS_AND_TITLES : CommitInfoChoice.NONE;
					mattermostNotifier.includeCustomAttachmentMessage = mattermostJobProperty.includeCustomAttachmentMessage();
					mattermostNotifier.customAttachmentMessage = mattermostJobProperty.getCustomAttachmentMessage();
					mattermostNotifier.includeCustomMessage = mattermostJobProperty.includeCustomMessage();
					mattermostNotifier.customMessage = mattermostJobProperty.getCustomMessage();
				}

				try {
					//property section is not used anymore - remove
					p.removeProperty(MattermostJobProperty.class);
					p.save();
					logger.info("Configuration updated successfully");
				} catch (IOException e) {
					logger.log(Level.SEVERE, e.getMessage(), e);
				}
			}
		}
	}
}
