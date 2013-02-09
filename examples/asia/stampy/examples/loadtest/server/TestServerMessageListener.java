/*
 * Copyright (C) 2013 Burton Alexander
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 * 
 */
package asia.stampy.examples.loadtest.server;

import java.util.ArrayList;
import java.util.List;

import org.apache.mina.core.session.IoSession;

import asia.stampy.client.message.ack.AckMessage;
import asia.stampy.common.HostPort;
import asia.stampy.common.message.StampyMessage;
import asia.stampy.common.message.StompMessageType;
import asia.stampy.common.mina.StampyMinaMessageListener;

/**
 * 
 *
 * @see TestServerMessageEvent
 */
public class TestServerMessageListener implements StampyMinaMessageListener {

	private List<String> acks = new ArrayList<>();

	private boolean connect = false;
	private boolean disconnect = false;
	
	private long start;
	private long end;

	/* (non-Javadoc)
	 * @see asia.stampy.common.mina.StampyMinaMessageListener#messageReceived(asia.stampy.common.message.StampyMessage, org.apache.mina.core.session.IoSession, asia.stampy.common.HostPort)
	 */
	@Override
	public void messageReceived(StampyMessage<?> message, IoSession session, HostPort hostPort) throws Exception {
		switch (message.getMessageType()) {
		case ACK:
			acks.add(((AckMessage) message).getHeader().getId());
			break;
		case CONNECT:
			connect = true;
			start = System.nanoTime();
			break;
		case DISCONNECT:
			disconnect = true;
			end = System.nanoTime();
			stats();
			break;
		default:
			System.out.println("Unexpected message " + message.getMessageType());
			break;

		}
	}

	private void stats() {
		System.out.println("# of acks: " + acks.size());
		System.out.println("Connect message? " + connect);
		System.out.println("Disconnect message? " + disconnect);
		long diff = end - start;
		System.out.println("Nano time elapsed: " + diff);
	}

	/* (non-Javadoc)
	 * @see asia.stampy.common.mina.StampyMinaMessageListener#isForMessage(asia.stampy.common.message.StampyMessage)
	 */
	@Override
	public boolean isForMessage(StampyMessage<?> message) {
		return true;
	}

	/* (non-Javadoc)
	 * @see asia.stampy.common.mina.StampyMinaMessageListener#getMessageTypes()
	 */
	@Override
	public StompMessageType[] getMessageTypes() {
		return StompMessageType.values();
	}

}