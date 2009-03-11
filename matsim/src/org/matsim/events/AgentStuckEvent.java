/* *********************************************************************** *
 * project: org.matsim.*
 * AgentStuckEvent.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007, 2008 by the members listed in the COPYING,  *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.events;

import org.matsim.interfaces.core.v01.Leg;
import org.matsim.interfaces.core.v01.Link;
import org.matsim.interfaces.core.v01.Person;

public class AgentStuckEvent extends AgentEvent {

	public static final String EVENT_TYPE = "stuckAndAbort";

	public AgentStuckEvent(final double time, final Person agent, final Link link, final Leg leg) {
		super(time, agent, link, leg);
	}

	public AgentStuckEvent(final double time, final String agentId, final String linkId) {
		super(time, agentId, linkId);
	}

	@Override
	public String getEventType() {
		return EVENT_TYPE;
	}

	@Override
	public String toString() {
		return asString() + "3\t" + EVENT_TYPE;
	}

}
