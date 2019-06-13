package jenkins.plugins.mattermost;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import hudson.EnvVars;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Hudson;
import hudson.model.Result;
import hudson.model.Run;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.AffectedFile;
import hudson.scm.ChangeLogSet.Entry;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.triggers.SCMTrigger;
import hudson.util.LogTaskListener;
import org.apache.commons.lang.StringUtils;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.SEVERE;


@SuppressWarnings("rawtypes")
public class ActiveNotifier implements FineGrainedNotifier {

	private static final Logger logger = Logger.getLogger(MattermostListener.class.getName());

	MattermostNotifier notifier;
	BuildListener listener;

	public ActiveNotifier(MattermostNotifier notifier, BuildListener listener) {
		super();
		this.notifier = notifier;
		this.listener = listener;
	}

	private MattermostService getMattermost(AbstractBuild r) {
		return notifier.newMattermostService(r, listener);
	}

	public void deleted(AbstractBuild r) {
	}

	public void started(AbstractBuild build) {

		//AbstractProject<?, ?> project = build.getProject();

		CauseAction causeAction = build.getAction(CauseAction.class);

		if (causeAction != null) {
			Cause scmCause = causeAction.findCause(SCMTrigger.SCMTriggerCause.class);
			if (scmCause == null) {
				MessageBuilder message = new MessageBuilder(notifier, build);
				message.append(causeAction.getShortDescription());
				notifyStart(build, message.appendOpenLink().toString());
				// Cause was found, exit early to prevent double-message
				return;
			}
		}

		String changes = getChanges(build, notifier.getIncludeCustomAttachmentMessage());
		if (changes != null) {
			notifyStart(build, changes);
		} else {
			notifyStart(build, getBuildStatusMessage(build, false, notifier.getIncludeCustomAttachmentMessage()));
		}
	}

	private void notifyStart(AbstractBuild build, String attachmentMessage) {
		AbstractProject<?, ?> project = (build != null) ? build.getProject() : null;
		AbstractBuild<?, ?> previousBuild = (project != null && project.getLastBuild() != null) ? project.getLastBuild().getPreviousCompletedBuild() : null;
		String expandedCustomMessage = getExpandedCustomMessage(build);
		if (previousBuild == null) {
			getMattermost(build).publish(attachmentMessage, expandedCustomMessage, "good");
		} else {
			getMattermost(build).publish(attachmentMessage, expandedCustomMessage, getBuildColor(previousBuild));
		}
	}

	public void finalized(AbstractBuild r) {
	}

	public void completed(AbstractBuild r) {
		AbstractProject<?, ?> project = r.getProject();
		Result result = r.getResult();
		AbstractBuild<?, ?> previousBuild = project.getLastBuild();
		if (previousBuild != null) {
			do {
				previousBuild = previousBuild.getPreviousCompletedBuild();
			} while (previousBuild != null && previousBuild.getResult() == Result.ABORTED);
		}
		Result previousResult = (previousBuild != null) ? previousBuild.getResult() : Result.SUCCESS;
		if ((result == Result.ABORTED && notifier.getNotifyAborted())
				|| (result == Result.FAILURE //notify only on single failed build
				&& previousResult != Result.FAILURE
				&& notifier.getNotifyFailure())
				|| (result == Result.FAILURE //notify only on repeated failures
				&& previousResult == Result.FAILURE
				&& notifier.getNotifyRepeatedFailure())
				|| (result == Result.NOT_BUILT && notifier.getNotifyNotBuilt())
				|| (result == Result.SUCCESS
				&& (previousResult == Result.FAILURE || previousResult == Result.UNSTABLE)
				&& notifier.getNotifyBackToNormal())
				|| (result == Result.SUCCESS && notifier.getNotifySuccess())
				|| (result == Result.UNSTABLE && notifier.getNotifyUnstable())) {
			String expandedCustomMessage = getExpandedCustomMessage(r);
			getMattermost(r).publish(getBuildStatusMessage(r, notifier.getIncludeTestSummary(),
					notifier.getIncludeCustomAttachmentMessage()), expandedCustomMessage, getBuildColor(r));
			if (notifier.getCommitInfoChoice().showAnything()) {
				getMattermost(r).publish(getCommitList(r), expandedCustomMessage, getBuildColor(r));
			}
		}
	}

	String getChanges(AbstractBuild r, boolean includeCustomAttachmentMessage) {
		if (!r.hasChangeSetComputed()) {
			logger.info("No change set computed...");
			return null;
		}
		ChangeLogSet changeSet = r.getChangeSet();
		List<Entry> entries = new LinkedList<Entry>();
		Set<AffectedFile> files = new HashSet<AffectedFile>();
		for (Object o : changeSet.getItems()) {
			Entry entry = (Entry)o;
			logger.info("Entry " + o);
			entries.add(entry);
			files.addAll(entry.getAffectedFiles());
		}
		if (entries.isEmpty()) {
			logger.info("Empty change...");
			return null;
		}
		Set<String> authors = new HashSet<String>();
		for (Entry entry : entries) {
			authors.add(entry.getAuthor().getDisplayName());
		}
		MessageBuilder message = new MessageBuilder(notifier, r);
		message.append(":pray: Started by changes from ");
		message.append(StringUtils.join(authors, ", "));
		message.append(" (");
		message.append(files.size());
		message.append(" file(s) changed)");
		message.appendOpenLink();
		if (includeCustomAttachmentMessage) {
			message.appendCustomAttachmentMessage();
		}
		return message.toString();
	}

	String getCommitList(AbstractBuild r) {
		ChangeLogSet changeSet = r.getChangeSet();
		List<Entry> entries = new LinkedList<Entry>();
		for (Object o : changeSet.getItems()) {
			Entry entry = (Entry)o;
			logger.info("Entry " + o);
			entries.add(entry);
		}
		if (entries.isEmpty()) {
			logger.info("Empty change...");
			Cause.UpstreamCause c = (Cause.UpstreamCause)r.getCause(Cause.UpstreamCause.class);
			if (c == null) {
				return "No Changes.";
			}
			String upProjectName = c.getUpstreamProject();
			int buildNumber = c.getUpstreamBuild();
			AbstractProject project = Hudson.getInstance().getItemByFullName(upProjectName, AbstractProject.class);
			if (project == null) {
				return "No upstream project.";
			}
			AbstractBuild upBuild = (AbstractBuild)project.getBuildByNumber(buildNumber);
			return getCommitList(upBuild);
		}
		Set<String> commits = new HashSet<String>();
		for (Entry entry : entries) {
			StringBuffer commit = new StringBuffer();
			CommitInfoChoice commitInfoChoice = notifier.getCommitInfoChoice();
			if (commitInfoChoice.showTitle()) {
				commit.append(entry.getMsg());
			}
			if (commitInfoChoice.showAuthor()) {
				commit.append(" [").append(entry.getAuthor().getDisplayName()).append("]");
			}
			commits.add(commit.toString());
		}
		MessageBuilder message = new MessageBuilder(notifier, r);
		message.append("Changes:\n- ");
		message.append(StringUtils.join(commits, "\n- "));
		return message.toString();
	}

	static String getBuildColor(AbstractBuild r) {
		Result result = r.getResult();
		if (result == Result.SUCCESS) {
			return "good";
		} else if (result == Result.FAILURE) {
			return "danger";
		} else {
			return "warning";
		}
	}

	String getBuildStatusMessage(AbstractBuild r, boolean includeTestSummary, boolean includeCustomAttachmentMessage) {
		MessageBuilder message = new MessageBuilder(notifier, r);
		message.appendStatusMessage();
		message.appendDuration();
		message.appendOpenLink();
		if (includeTestSummary) {
			message.appendTestSummary();
		}
		if (includeCustomAttachmentMessage) {
			message.appendCustomAttachmentMessage();
		}
		return message.toString();
	}

	String getExpandedCustomMessage(AbstractBuild build) {
		String result = "";
		if(notifier.getIncludeCustomMessage()) {
			String customMessage = notifier.getCustomMessage();
			EnvVars envVars = new EnvVars();
			try {
				envVars = build.getEnvironment(new LogTaskListener(logger, INFO));
			} catch (IOException | InterruptedException e) {
				logger.log(SEVERE, e.getMessage(), e);
			}
			result = envVars.expand(customMessage);
		}
		return result;
	}

	public static class MessageBuilder {

		private static final String STARTING_STATUS_MESSAGE = ":pray: Starting...",
			BACK_TO_NORMAL_STATUS_MESSAGE = ":white_check_mark: Back to normal",
			STILL_FAILING_STATUS_MESSAGE = ":no_entry_sign: Still Failing",
			SUCCESS_STATUS_MESSAGE = ":white_check_mark: Success",
			FAILURE_STATUS_MESSAGE = ":no_entry_sign: Failure",
			ABORTED_STATUS_MESSAGE = ":warning: Aborted",
			NOT_BUILT_STATUS_MESSAGE = ":warning: Not built",
			UNSTABLE_STATUS_MESSAGE = ":warning: Unstable",
			UNKNOWN_STATUS_MESSAGE = ":question: Unknown";

		private StringBuffer message;
		private MattermostNotifier notifier;
		private AbstractBuild build;

		public MessageBuilder(MattermostNotifier notifier, AbstractBuild build) {
			this.notifier = notifier;
			this.message = new StringBuffer();
			this.build = build;
			startMessage();
		}

		public MessageBuilder appendStatusMessage() {
			message.append(this.escape(getStatusMessage(build)));
			return this;
		}

		static String getStatusMessage(AbstractBuild r) {
			if (r.isBuilding()) {
				return STARTING_STATUS_MESSAGE;
			}
			Result result = r.getResult();
			Result previousResult;
			Run lastBuild = r.getProject().getLastBuild();
			Run previousBuild = (lastBuild != null) ? lastBuild.getPreviousBuild() : null;
			Run previousSuccessfulBuild = r.getPreviousSuccessfulBuild();
			boolean buildHasSucceededBefore = previousSuccessfulBuild != null;

			/*
			 * If the last build was aborted, go back to find the last non-aborted build.
			 * This is so that aborted builds do not affect build transitions.
			 * I.e. if build 1 was failure, build 2 was aborted and build 3 was a success the transition
			 * should be failure -> success (and therefore back to normal) not aborted -> success.
			 */
			Run lastNonAbortedBuild = previousBuild;
			while (lastNonAbortedBuild != null && lastNonAbortedBuild.getResult() == Result.ABORTED) {
				lastNonAbortedBuild = lastNonAbortedBuild.getPreviousBuild();
			}


			/* If all previous builds have been aborted, then use
			 * SUCCESS as a default status so an aborted message is sent
			 */
			if (lastNonAbortedBuild == null) {
				previousResult = Result.SUCCESS;
			} else {
				previousResult = lastNonAbortedBuild.getResult();
			}

			/* Back to normal should only be shown if the build has actually succeeded at some point.
			 * Also, if a build was previously unstable and has now succeeded the status should be
			 * "Back to normal"
			 */
			if (result == Result.SUCCESS
					&& (previousResult == Result.FAILURE || previousResult == Result.UNSTABLE)
					&& buildHasSucceededBefore) {
				return BACK_TO_NORMAL_STATUS_MESSAGE;
			}
			if (result == Result.FAILURE && previousResult == Result.FAILURE) {
				return STILL_FAILING_STATUS_MESSAGE;
			}
			if (result == Result.SUCCESS) {
				return SUCCESS_STATUS_MESSAGE;
			}
			if (result == Result.FAILURE) {
				return FAILURE_STATUS_MESSAGE;
			}
			if (result == Result.ABORTED) {
				return ABORTED_STATUS_MESSAGE;
			}
			if (result == Result.NOT_BUILT) {
				return NOT_BUILT_STATUS_MESSAGE;
			}
			if (result == Result.UNSTABLE) {
				return UNSTABLE_STATUS_MESSAGE;
			}
			return UNKNOWN_STATUS_MESSAGE;
		}

		public MessageBuilder append(String string) {
			message.append(this.escape(string));
			return this;
		}

		public MessageBuilder append(Object string) {
			message.append(this.escape(string.toString()));
			return this;
		}

		private MessageBuilder startMessage() {
			message.append(this.escapeDisplayName(build.getProject().getFullDisplayName()));
			message.append(" - ");
			message.append(this.escapeDisplayName(build.getDisplayName()));
			message.append(" ");
			return this;
		}

		public MessageBuilder appendOpenLink() {
			String url = notifier.getBuildServerUrl() + build.getUrl();
			message.append(" [Open](").append(url).append(")");
			return this;
		}

		public MessageBuilder appendDuration() {
			message.append(" after ");
			String durationString;
			if (message.toString().contains(BACK_TO_NORMAL_STATUS_MESSAGE)) {
				durationString = createBackToNormalDurationString();
			} else {
				durationString = build.getDurationString();
			}
			message.append(durationString);
			return this;
		}

		public MessageBuilder appendTestSummary() {
			AbstractTestResultAction<?> action = this.build
					.getAction(AbstractTestResultAction.class);
			if (action != null) {
				int total = action.getTotalCount();
				int failed = action.getFailCount();
				int skipped = action.getSkipCount();
				message.append("\nTest Status:\n");
				message.append("\tPassed: " + (total - failed - skipped));
				message.append(", Failed: " + failed);
				message.append(", Skipped: " + skipped);
			} else {
				message.append("\nNo Tests found.");
			}
			return this;
		}

		public MessageBuilder appendCustomAttachmentMessage() {
			String customAttachmentMessage = notifier.getCustomAttachmentMessage();
			EnvVars envVars = new EnvVars();
			try {
				envVars = build.getEnvironment(new LogTaskListener(logger, INFO));
			} catch (IOException e) {
				logger.log(SEVERE, e.getMessage(), e);
			} catch (InterruptedException e) {
				logger.log(SEVERE, e.getMessage(), e);
			}
			message.append("\n");
			message.append(envVars.expand(customAttachmentMessage));
			return this;
		}

		private String createBackToNormalDurationString() {
			Run previousSuccessfulBuild = build.getPreviousSuccessfulBuild();
			if (previousSuccessfulBuild == null) {
				return "unknown";
			}
			long previousSuccessStartTime = previousSuccessfulBuild.getStartTimeInMillis();
			long previousSuccessDuration = previousSuccessfulBuild.getDuration();
			long previousSuccessEndTime = previousSuccessStartTime + previousSuccessDuration;
			long buildStartTime = build.getStartTimeInMillis();
			long buildDuration = build.getDuration();
			long buildEndTime = buildStartTime + buildDuration;
			long backToNormalDuration = buildEndTime - previousSuccessEndTime;
			return Util.getTimeSpanString(backToNormalDuration);
		}

		public String escape(String string) {
			string = string.replace("&", "&amp;");
			string = string.replace("<", "&lt;");
			string = string.replace(">", "&gt;");

			return string;
		}

		public String escapeDisplayName(String displayName) {
			// escape HTML
			displayName = escape(displayName);

			// escape mattermost markdown which _may_ occur in job display names
			displayName = displayName.replace("~", "\\~");
			displayName = displayName.replace("*", "\\*");
			displayName = displayName.replace("_", "\\_");
			displayName = displayName.replace("`", "\\`");

			return displayName;
		}

		public String toString() {
			return message.toString();
		}
	}
}
