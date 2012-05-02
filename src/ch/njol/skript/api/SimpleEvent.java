/*
 *   This file is part of Skript.
 *
 *  Skript is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Skript is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Skript.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * 
 * Copyright 2011, 2012 Peter Güttinger
 * 
 */

package ch.njol.skript.api;

import java.util.regex.Matcher;

import org.bukkit.event.Event;

/**
 * A very basic SkriptEvent which returns true for all events (i.e. all registered events). This event is especially useful for custom events.
 * 
 * @author Peter Güttinger
 */
public class SimpleEvent extends SkriptEvent {
	
	public SimpleEvent() {}
	
	@Override
	public boolean check(final Event e) {
		return true;
	}
	
	@Override
	public void init(final Object[][] args, final int matchedPattern, final Matcher matcher) {}
	
	@Override
	public String getDebugMessage(final Event e) {
		return "simple event";
	}
	
}