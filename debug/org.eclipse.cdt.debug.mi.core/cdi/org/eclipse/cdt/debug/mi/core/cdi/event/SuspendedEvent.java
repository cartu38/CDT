/*******************************************************************************
 * Copyright (c) 2000, 2006 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     QNX Software Systems - Initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.debug.mi.core.cdi.event;

import org.eclipse.cdt.debug.core.cdi.ICDISessionObject;
import org.eclipse.cdt.debug.core.cdi.event.ICDISuspendedEvent;
import org.eclipse.cdt.debug.core.cdi.model.ICDIObject;
import org.eclipse.cdt.debug.mi.core.cdi.BreakpointHit;
import org.eclipse.cdt.debug.mi.core.cdi.BreakpointManager;
import org.eclipse.cdt.debug.mi.core.cdi.EndSteppingRange;
import org.eclipse.cdt.debug.mi.core.cdi.ErrorInfo;
import org.eclipse.cdt.debug.mi.core.cdi.EventBreakpointHit;
import org.eclipse.cdt.debug.mi.core.cdi.FunctionFinished;
import org.eclipse.cdt.debug.mi.core.cdi.Session;
import org.eclipse.cdt.debug.mi.core.cdi.SharedLibraryEvent;
import org.eclipse.cdt.debug.mi.core.cdi.SignalReceived;
import org.eclipse.cdt.debug.mi.core.cdi.WatchpointScope;
import org.eclipse.cdt.debug.mi.core.cdi.WatchpointTrigger;
import org.eclipse.cdt.debug.mi.core.cdi.model.Breakpoint;
import org.eclipse.cdt.debug.mi.core.cdi.model.EventBreakpoint;
import org.eclipse.cdt.debug.mi.core.cdi.model.Target;
import org.eclipse.cdt.debug.mi.core.event.MIBreakpointHitEvent;
import org.eclipse.cdt.debug.mi.core.event.MICatchpointHitEvent;
import org.eclipse.cdt.debug.mi.core.event.MIErrorEvent;
import org.eclipse.cdt.debug.mi.core.event.MIEvent;
import org.eclipse.cdt.debug.mi.core.event.MIFunctionFinishedEvent;
import org.eclipse.cdt.debug.mi.core.event.MILocationReachedEvent;
import org.eclipse.cdt.debug.mi.core.event.MISharedLibEvent;
import org.eclipse.cdt.debug.mi.core.event.MISignalEvent;
import org.eclipse.cdt.debug.mi.core.event.MISteppingRangeEvent;
import org.eclipse.cdt.debug.mi.core.event.MIWatchpointScopeEvent;
import org.eclipse.cdt.debug.mi.core.event.MIWatchpointTriggerEvent;

/**
 *
 */
public class SuspendedEvent implements ICDISuspendedEvent {

	MIEvent event;
	Session session;

	public SuspendedEvent(Session s, MIEvent e) {
		session = s;
		event = e;
	}

	public ICDISessionObject getReason() {
		if (event instanceof MIBreakpointHitEvent) {
			BreakpointManager bkptMgr = session.getBreakpointManager();
			Breakpoint bkpt = bkptMgr.getBreakpoint(event.getMISession(), ((MIBreakpointHitEvent)event).getNumber());
			// In versions prior to 7.0, a catchpoint (Event Breakpoint in
			// CDT speak) is reported by gdb as a generic stopped event; gdb
			// does not indicate it was caused by a breakpoint. In 7.0 and
			// above, it does. Here we handle the >= 7.0 case. In the < 7.0
			// case, we generate a MICatchpointHitEvent, and that's handled
			// below
			if (bkpt instanceof EventBreakpoint) {
				return new EventBreakpointHit(session, EventBreakpoint.getGdbEventFromId(((EventBreakpoint)bkpt).getEventType()));
			}
			else {
				return new BreakpointHit(session, (MIBreakpointHitEvent)event);
			}
		} else if (event instanceof MIWatchpointTriggerEvent) {
			return new WatchpointTrigger(session, (MIWatchpointTriggerEvent)event);
		} else if (event instanceof MIWatchpointScopeEvent) {
			return new WatchpointScope(session, (MIWatchpointScopeEvent)event);
		} else if (event instanceof MISteppingRangeEvent) {
			return new EndSteppingRange(session);
		} else if (event instanceof MISignalEvent) {
			return new SignalReceived(session, (MISignalEvent)event);
		} else if (event instanceof MILocationReachedEvent) {
			return new EndSteppingRange(session);
		} else if (event instanceof MIFunctionFinishedEvent) {
			return new FunctionFinished(session, (MIFunctionFinishedEvent)event);
		} else if (event instanceof MIErrorEvent) {
			return new ErrorInfo(session, (MIErrorEvent)event);
		} else if (event instanceof MISharedLibEvent) {
			return new SharedLibraryEvent(session);
		} else if (event instanceof MICatchpointHitEvent) {
			// See note above. If we get here, we're dealing with a gdb < 7.0
			return new EventBreakpointHit(session, ((MICatchpointHitEvent)event).getCatchpointType());
		}
		return session;
	}

	/**
	 * @see org.eclipse.cdt.debug.core.cdi.event.ICDIEvent#getSource()
	 */
	public ICDIObject getSource() {
		Target target = session.getTarget(event.getMISession()); 
		// We can send the target as the Source.  CDI
		// Will assume that all threads are supended for this.
		// This is true for gdb when it suspend the inferior
		// all threads are suspended.
		return target;
	}
}
