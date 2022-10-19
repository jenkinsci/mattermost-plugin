package jenkins.plugins.mattermost;

import hudson.ProxyConfiguration;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.ssl.SSLContexts;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class StandardMattermostService implements MattermostService
{

	private static final Logger logger = Logger.getLogger(StandardMattermostService.class.getName());

	private String endpoint;
	private final String[] channelIds;
	private final String icon;

	public StandardMattermostService(String endpoint, String channelId, String icon)
	{
		super();
		this.endpoint = endpoint;
		this.channelIds = channelId.split("[,;]+");
		this.icon = icon;
	}

	private static JSONObject createPayload(String message, String text, String color, String roomId, String userId, String icon)
	{
		JSONObject json = new JSONObject();
		JSONObject field = new JSONObject();
		field.put("short", false);
		field.put("value", message);
		JSONArray fields = new JSONArray();
		fields.put(field);

		JSONObject attachment = new JSONObject();
		attachment.put("fallback", message);
		attachment.put("color", color);
		attachment.put("fields", fields);
		JSONArray mrkdwn = new JSONArray();
		mrkdwn.put("pretext");
		mrkdwn.put("text");
		mrkdwn.put("fields");
		attachment.put("mrkdwn_in", mrkdwn);
		JSONArray attachments = new JSONArray();
		attachments.put(attachment);
		json.put("text", text);
		json.put("attachments", attachments);

		if (!roomId.isEmpty()) json.put("channel", roomId);
		json.put("username", userId);
		json.put("icon_url", icon);
		return json;
	}

	public static String createRegexFromGlob(String glob)
	{
		StringBuilder out = new StringBuilder("^");
		for (int i = 0; i < glob.length(); ++i)
		{
			final char c = glob.charAt(i);
			switch (c)
			{
				case '*':
					out.append(".*");
					break;
				case '?':
					out.append('.');
					break;
				case '.':
					out.append("\\.");
					break;
				case '\\':
					out.append("\\\\");
					break;
				default:
					out.append(c);
			}
		}
		out.append('$');
		return out.toString();
	}

	private static MattermostNotifier.DescriptorImpl getServiceDescriptor(){
		MattermostNotifier.DescriptorImpl mattermostNotifierDescriptor =
				Jenkins.get().getDescriptorByType(MattermostNotifier.DescriptorImpl.class);

		// taken from other class, not really sure about the use case here :)
		if (mattermostNotifierDescriptor == null){
			logger.fine("Could not get MattermostNotifier descriptor by class, trying by ID..");
			mattermostNotifierDescriptor = (MattermostNotifier.DescriptorImpl) Jenkins.get().getDescriptor("mattermostNotifier");//junit test fallback
		}
		return mattermostNotifierDescriptor;
	}

	public boolean publish(String message)
	{
		return publish(message, "warning");
	}

	public boolean publish(String message, String color)
	{
		return publish(message, "", color);
	}

	public boolean publish(String message, String text, String color)
	{
		boolean result = true;
		for (String userAndRoomId : channelIds)
		{
			//String url = endpoint;
			URL url;
			try
			{

				String roomId = userAndRoomId.trim();
				String userId = "jenkins";
				url = new URL(this.endpoint);
				HttpHost httpHost = new HttpHost(url.getHost(), url.getPort(), url.getProtocol());
				HttpClientBuilder clientBuilder = HttpClients.custom();
				clientBuilder.setSSLContext(SSLContexts.createDefault());
				clientBuilder.setRedirectStrategy(new LaxRedirectStrategy());
				RequestConfig.Builder reqconfigconbuilder = RequestConfig.custom();
				reqconfigconbuilder.setConnectTimeout(10000);
				reqconfigconbuilder.setSocketTimeout(10000);

				ProxyConfiguration globalProxy = Jenkins.get().proxy;
				MattermostNotifier.DescriptorImpl serviceDescriptor = getServiceDescriptor();

				if (serviceDescriptor.isUseCustomProxy()) {

					ProxyConfiguration customProxy = new ProxyConfiguration(
							serviceDescriptor.getProxyServer(),
							serviceDescriptor.getProxyPort(),
							serviceDescriptor.getProxyUsername(),
							serviceDescriptor.getProxyPassword().getPlainText(),
							null,
							null);
					setupProxy(customProxy, clientBuilder, reqconfigconbuilder);
					logger.info("MattermostNotifier using custom proxy: "+ customProxy.name + ":" + customProxy.port);
				} else if (globalProxy != null && isProxyRequired(ProxyConfiguration.getNoProxyHostPatterns(globalProxy.noProxyHost)))
				{
					setupProxy(globalProxy, clientBuilder, reqconfigconbuilder);
				}

				RequestConfig config = reqconfigconbuilder.build();
				CloseableHttpClient client = clientBuilder.build();
				RequestBuilder requestBuilder = RequestBuilder.post(url.toURI());
				requestBuilder.setConfig(config);
				requestBuilder.setCharset(StandardCharsets.UTF_8);

				// Supported channel string formats:
				// - user@channel
				// - user@@dmchannel
				// - channel
				// - @dmchannel
				int atPos = userAndRoomId.indexOf("@");
				if (atPos > 0 && atPos < userAndRoomId.length() - 1)
				{
					userId = userAndRoomId.substring(0, atPos).trim();
					roomId = userAndRoomId.substring(atPos + 1).trim();
				}

				String roomIdString = roomId;
				if (roomIdString.isEmpty())
				{
					roomIdString = "(default)";
				}


				JSONObject json = createPayload(message, text, color, roomId, userId, icon);
				logger.info("Playload: " + json.toString());
				requestBuilder.setEntity(new StringEntity(json.toString(), ContentType.APPLICATION_JSON));
				CloseableHttpResponse execute = client.execute(httpHost, requestBuilder.build());
				int responseCode = execute.getStatusLine().getStatusCode();
				if (responseCode != HttpStatus.SC_OK)
				{
					result = false;
					logHttpErrorStatus(execute, responseCode, roomIdString, url);
				} else
					logger.info("Status " + responseCode + ": to " + roomIdString + "@" + url.getHost() + "/***: " + message + " (" + color + ")");
			} catch(java.net.URISyntaxException | java.io.IOException e)
			{
				logger.log(Level.WARNING, "Error posting to Mattermost", e);
				result = false;
			}
		}
		return result;
	}

	private void logHttpErrorStatus(CloseableHttpResponse execute, int responseCode, String roomIdString, URL hosturl) throws IOException
	{
		BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(execute.getEntity().getContent(), Charset.defaultCharset()));
		try {
			String collect = bufferedReader.lines().collect(Collectors.joining(" "));
			logger.log(Level.WARNING, "WARN Status " + responseCode + ": to " + roomIdString + "@" + hosturl.getHost() + ": " + collect);
		} finally {
			bufferedReader.close();
		}
	}

	private RequestConfig.Builder setupProxy(ProxyConfiguration proxy, HttpClientBuilder clientBuilder, RequestConfig.Builder reqconfigconbuilder) throws MalformedURLException
	{
		HttpHost proxyHost = new HttpHost(proxy.name, proxy.port);
		DefaultProxyRoutePlanner routePlanner = new DefaultProxyRoutePlanner(proxyHost);
		clientBuilder.setRoutePlanner(routePlanner);
		reqconfigconbuilder.setProxy(proxyHost);

		setupProxyAuth(proxy, clientBuilder, proxyHost);
		return reqconfigconbuilder;
	}

	private void setupProxyAuth(ProxyConfiguration proxy, HttpClientBuilder clientBuilder, HttpHost proxyHost)
	{
		String username = proxy.getUserName();
		String password = proxy.getPassword();
		// Consider it to be passed if username specified. Sufficient?
		if (username != null && !username.isEmpty())
		{
			logger.info("Using proxy authentication (user=" + username + ")");
			BasicCredentialsProvider basicCredentialsProvider = new BasicCredentialsProvider();
			basicCredentialsProvider.setCredentials(
					new org.apache.http.auth.AuthScope(proxyHost.getHostName(), proxy.port),
					new org.apache.http.auth.UsernamePasswordCredentials(username, password));

			clientBuilder.setDefaultCredentialsProvider(basicCredentialsProvider);
		}
	}

	protected boolean isProxyRequired(List<Pattern> noProxyHosts)
	{
		try
		{
			URL url = new URL(endpoint);
			for (Pattern p : noProxyHosts)
			{
				if (p.matcher(url.getHost()).matches()) return false;
			}
		} catch (MalformedURLException e)
		{
			logger.log(
					Level.WARNING,
					"A malformed URL [" + endpoint + "] is defined as endpoint, please check your settings");
			// default behavior : proxy still activated
			return true;
		}
		return true;
	}

	@Deprecated
	protected boolean isProxyRequired(String... noProxyHost)
	{//
		if (noProxyHost == null)
			return false;
		List<String> lst = Arrays.asList(noProxyHost);
		List<Pattern> collect = lst.stream()
				.filter(Objects::nonNull)
				.map(StandardMattermostService::createRegexFromGlob)
				.map(Pattern::compile)
				.collect(Collectors.toList());
		return isProxyRequired(collect);
	}

	void setEndpoint(String endpoint)
	{
		this.endpoint = endpoint;
	}
}
