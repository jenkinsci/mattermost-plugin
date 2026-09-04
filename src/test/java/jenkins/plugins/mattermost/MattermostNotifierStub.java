package jenkins.plugins.mattermost;

public class MattermostNotifierStub extends MattermostNotifier {

  public MattermostNotifierStub(
      String host,
      String room,
      String icon,
      String buildServerUrl,
      String sendAs,
      boolean startNotification,
      boolean notifyAborted,
      boolean notifyFailure,
      boolean notifyNotBuilt,
      boolean notifySuccess,
      boolean notifyUnstable,
      boolean notifyBackToNormal,
      boolean notifyRepeatedFailure,
      boolean includeTestSummary,
      CommitInfoChoice commitInfoChoice,
      boolean includeCustomAttachmentMessage,
      String customAttachmentMessage,
      boolean includeCustomMessage,
      String customMessage) {
    super(
            host, //!= null ? Secret.fromString(host) : null,
            room,
            icon,
            buildServerUrl,
            sendAs,
            startNotification,
            notifyAborted,
            notifyFailure,
            notifyNotBuilt,
            notifySuccess,
            notifyUnstable,
        notifyBackToNormal,
        notifyRepeatedFailure,
        includeTestSummary,
        commitInfoChoice,
        includeCustomAttachmentMessage,
        customAttachmentMessage,
        includeCustomMessage,
        customMessage);
  }

	public static class DescriptorStub extends MattermostNotifier.DescriptorImpl
	{

    private MattermostService mattermostService;

    @Override
    public synchronized void load() {}

    @Override
    MattermostService getMattermostService(
        final String host, final String room, final String icon) {
      return mattermostService;
    }

    public void setMattermostService(MattermostService mattermostService) {
      this.mattermostService = mattermostService;
    }
  }
}
