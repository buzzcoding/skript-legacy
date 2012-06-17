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

package ch.njol.skript.expressions.base;

import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.bukkit.event.Event;

import ch.njol.skript.Skript;
import ch.njol.skript.TriggerFileLoader;
import ch.njol.skript.api.Changer;
import ch.njol.skript.api.Changer.ChangeMode;
import ch.njol.skript.api.Changer.ChangerUtils;
import ch.njol.skript.api.DefaultExpression;
import ch.njol.skript.api.Getter;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SimpleExpression;
import ch.njol.skript.lang.SkriptParser.ParseResult;

/**
 * A useful class for creating default expressions. It simply returns the event value of the given type.<br/>
 * This class can be used as default expression with <code>new EventValueExpression&lt;T&gt;(T.class)</code> or extended to make it manually placeable in expressions with:
 * 
 * <pre>
 * class MyExpression extends EventValueExpression&lt;T&gt; {
 * public MyExpression() {
 * 	super(T.class);
 * }
 * </pre>
 * 
 * @author Peter Güttinger
 * @see Skript#registerClass(ch.njol.skript.api.ClassInfo)
 */
public class EventValueExpression<T> extends SimpleExpression<T> implements DefaultExpression<T> {
	
	private final Class<T> c;
	private final T[] one;
	private final Changer<T, ?> changer;
	private final Map<Class<? extends Event>, Getter<? extends T, ?>> getters = new HashMap<Class<? extends Event>, Getter<? extends T, ?>>();
	
	public EventValueExpression(final Class<T> c) {
		this(c, null);
	}
	
	@SuppressWarnings("unchecked")
	public EventValueExpression(final Class<T> c, final Changer<T, ?> changer) {
		this.c = c;
		one = (T[]) Array.newInstance(c, 1);
		this.changer = changer;
	}
	
	@Override
	protected T[] getAll(final Event e) {
		if ((one[0] = getValue(e)) == null)
			return null;
		return one;
	}
	
	@SuppressWarnings("unchecked")
	private <E extends Event> T getValue(final E e) {
		final Getter<? extends T, ? super E> g = (Getter<? extends T, ? super E>) getters.get(e.getClass());
		if (g != null)
			return g.get(e);
		
		for (final Entry<Class<? extends Event>, Getter<? extends T, ?>> p : getters.entrySet()) {
			if (p.getKey().isAssignableFrom(e.getClass()))
				return ((Getter<? extends T, ? super E>) p.getValue()).get(e);
		}
		
		return null;
	}
	
	@Override
	public boolean init(final Expression<?>[] vars, final int matchedPattern, final ParseResult parser) {
		return init();
	}
	
	@Override
	public boolean init() {
		boolean hasValue = false;
		for (final Class<? extends Event> e : TriggerFileLoader.currentEvents) {
			if (getters.containsKey(e)) {
				hasValue = true;
				continue;
			}
			final Getter<? extends T, ?> getter = Skript.getEventValueGetter(e, c, getTime());
			if (getter != null) {
				getters.put(e, getter);
				hasValue = true;
			}
		}
		if (!hasValue) {
			Skript.error("There's no " + Skript.getExactClassName(c) + " in this event");
			return false;
		}
		return true;
	}
	
	@Override
	public Class<T> getReturnType() {
		return c;
	}
	
	@Override
	public boolean isSingle() {
		return true;
	}
	
	@Override
	public String getDebugMessage(final Event e) {
		if (e == null)
			return "event-" + Skript.getExactClassName(c);
		return Skript.getDebugMessage(getValue(e));
	}
	
	@Override
	public Class<?> acceptChange(final ChangeMode mode) {
		if (changer == null)
			return null;
		return changer.acceptChange(mode);
	}
	
	@Override
	public void change(final Event e, final Object delta, final ChangeMode mode) {
		if (changer == null)
			throw new UnsupportedOperationException();
		ChangerUtils.change(changer, this.getArray(e), delta, mode);
	}
	
	@Override
	public String toString() {
		return "event-" + Skript.getExactClassName(c);
	}
	
	@Override
	public boolean setTime(final int time) {
		for (final Class<? extends Event> e : TriggerFileLoader.currentEvents) {
			if (Skript.doesEventValueHaveTimeStates(e, c)) {
				super.setTime(time);
				return true;
			}
		}
		return false;
	}
	
	/**
	 * @return true
	 */
	@Override
	public boolean isDefault() {
		return true;
	}
}