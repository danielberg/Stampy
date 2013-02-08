package asia.stampy.common.mina.raw;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.lang.invoke.MethodHandles;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang.StringUtils;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import asia.stampy.common.HostPort;
import asia.stampy.common.StompMessageParser;
import asia.stampy.common.UnparseableException;
import asia.stampy.common.message.StampyMessage;
import asia.stampy.common.message.StampyMessageType;
import asia.stampy.common.mina.StampyMinaHandler;

public abstract class StampyRawStringHandler extends StampyMinaHandler {
	private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private Map<HostPort, String> messageParts = new ConcurrentHashMap<>();

	@Override
	public void messageReceived(final IoSession session, Object message) throws Exception {
		final HostPort hostPort = new HostPort((InetSocketAddress) session.getRemoteAddress());
		log.debug("Received raw message {} from {}", message, hostPort);

		resetHeartbeat(hostPort);

		if (!isValidObject(message)) {
			log.error("Object {} is not a valid STOMP message, closing connection {}", message, hostPort);
			illegalAccess(session);
			return;
		}

		final String msg = (String) message;

		Runnable runnable = new Runnable() {

			@Override
			public void run() {
				asyncProcessing(session, hostPort, msg);
			}
		};

		getExecutor().execute(runnable);
	}

	@Override
	public ProtocolCodecFactory getFactory(int maxMessageSize) {
		return new StringCodecFactory(maxMessageSize);
	}

	protected void asyncProcessing(IoSession session, HostPort hostPort, String msg) {
		try {
			String existing = messageParts.get(hostPort);
			if (StringUtils.isEmpty(existing)) {
				if (isStompMessage(msg)) {
					processMessage(msg, session, hostPort);
				} else {
					log.error("Message {} is not a valid STOMP message, closing connection {}", msg, hostPort);
					illegalAccess(session);
				}
			} else {
				String concat = existing + msg;
				processMessage(concat, session, hostPort);
			}
		} catch (Exception e) {
			log.error("Unexpected exception processing message " + msg + " for " + hostPort, e);
		}
	}

	private void processMessage(String msg, IoSession session, HostPort hostPort) throws UnparseableException, Exception,
			IOException {
		if (isHeartbeat(msg)) {
			log.debug("Simple heartbeat received");
			return;
		}

		int length = msg.length();
		int idx = msg.indexOf(StompMessageParser.EOM);

		if (idx == length - 1) {
			log.debug("Creating StampyMessage from {}", msg);
			processStompMessage(msg, session, hostPort);
		} else if (idx > 0) {
			log.debug("Multiple messages detected, parsing {}", msg);
			processMultiMessages(msg, session, hostPort);
		} else {
			messageParts.put(hostPort, msg);
			log.debug("Message part {} stored for {}", msg, hostPort);
		}
	}

	private void processMultiMessages(String msg, IoSession session, HostPort hostPort) throws UnparseableException,
			Exception, IOException {
		int idx = msg.indexOf(StompMessageParser.EOM);
		String fullMessage = msg.substring(0, idx + 1);
		String partMessage = msg.substring(idx);
		if(partMessage.startsWith(StompMessageParser.EOM)) {
			partMessage = partMessage.substring(1);
		}

		processStompMessage(fullMessage, session, hostPort);

		processMessage(partMessage, session, hostPort);
	}

	private void processStompMessage(String msg, IoSession session, HostPort hostPort) throws UnparseableException,
			Exception {
		securityCheck(msg, session);
		messageParts.remove(hostPort);
		StampyMessage<?> sm = getParser().parseMessage(msg);
		if (isValidMessage(sm)) {
			notifyListeners(sm, session, hostPort);
			sendResponseIfRequired(sm, session, hostPort);
		}
	}

	private boolean isStompMessage(String msg) throws IOException {
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new StringReader(msg));
			String stompMessageType = reader.readLine();
			if(isHeartbeat(stompMessageType)) return true;
			
			StampyMessageType type = StampyMessageType.valueOf(stompMessageType);
			return type != null;
		} catch (Exception e) {
			log.error("Unexpected exception parsing " + msg, e);
		} finally {
			if (reader != null) reader.close();
		}

		return false;
	}

}