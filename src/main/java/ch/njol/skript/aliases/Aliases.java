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
 * Copyright 2011-2013 Peter Güttinger
 * 
 */

package ch.njol.skript.aliases;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;

import ch.njol.skript.ScriptLoader;
import ch.njol.skript.Skript;
import ch.njol.skript.SkriptConfig;
import ch.njol.skript.config.Config;
import ch.njol.skript.config.EntryNode;
import ch.njol.skript.config.Node;
import ch.njol.skript.config.SectionNode;
import ch.njol.skript.config.validate.SectionValidator;
import ch.njol.skript.localization.ArgsMessage;
import ch.njol.skript.localization.Language;
import ch.njol.skript.localization.Message;
import ch.njol.skript.localization.Noun;
import ch.njol.skript.localization.RegexMessage;
import ch.njol.skript.log.RetainingLogHandler;
import ch.njol.skript.log.SkriptLogger;
import ch.njol.skript.util.EnchantmentType;
import ch.njol.skript.util.Utils;
import ch.njol.util.Pair;
import ch.njol.util.Setter;

/**
 * @author Peter Güttinger
 */
public abstract class Aliases {
	
	/**
	 * Note to self: never use this, use {@link #getAlias_i(String)} instead.
	 */
	private final static HashMap<String, ItemType> aliases = new HashMap<String, ItemType>(2500);
	
	private final static ItemType getAlias_i(final String s) {
		final ItemType t = ScriptLoader.currentAliases.get(s);
		if (t != null)
			return t;
		return aliases.get(s);
	}
	
	private final static HashMap<Integer, MaterialName> materialNames = new HashMap<Integer, MaterialName>(Material.values().length);
	
	private static String itemSingular = "item", itemPlural = "items", blockSingular = "block", blockPlural = "blocks";
	
	// this is not an alias!
	private final static ItemType everything = new ItemType();
	static {
		everything.setAll(true);
		everything.add(new ItemData());
	}
	
	private final static Message m_brackets_error = new Message("aliases.brackets error");
	private final static ArgsMessage m_empty_alias = new ArgsMessage("aliases.empty alias");
	private final static ArgsMessage m_unknown_variation = new ArgsMessage("aliases.unknown variation");
	private final static Message m_starting_with_number = new Message("aliases.starting with number");
	private final static Message m_missing_aliases = new Message("aliases.missing aliases");
	private final static Message m_empty_string = new Message("aliases.empty string");
	private final static ArgsMessage m_invalid_item_data = new ArgsMessage("aliases.invalid item data");
	private final static ArgsMessage m_invalid_id = new ArgsMessage("aliases.invalid id");
	private final static Message m_invalid_block_data = new Message("aliases.invalid block data");
	private final static ArgsMessage m_invalid_item_type = new ArgsMessage("aliases.invalid item type");
	private final static ArgsMessage m_out_of_data_range = new ArgsMessage("aliases.out of data range");
	private final static Message m_invalid_range = new Message("aliases.invalid range");
	private final static ArgsMessage m_invalid_section = new ArgsMessage("aliases.invalid section");
	private final static ArgsMessage m_section_not_found = new ArgsMessage("aliases.section not found");
	private final static ArgsMessage m_not_a_section = new ArgsMessage("aliases.not a section");
	private final static Message m_unexpected_non_variation_section = new Message("aliases.unexpected non-variation section");
	private final static Message m_unexpected_section = new Message("aliases.unexpected section");
	private final static ArgsMessage m_loaded_x_aliases_from = new ArgsMessage("aliases.loaded x aliases from");
	private final static ArgsMessage m_loaded_x_aliases = new ArgsMessage("aliases.loaded x aliases");
	
	/**
	 * @param name mixedcase string
	 * @param value
	 * @param variations
	 * @return
	 */
	private static HashMap<String, ItemType> getAliases(final String name, final ItemType value, final HashMap<String, HashMap<String, ItemType>> variations) {
		final LinkedHashMap<String, ItemType> r = new LinkedHashMap<String, ItemType>(); // LinkedHashMap to preserve order for item names
		Matcher m;
		if ((m = Pattern.compile("\\[([^\\[\\]]+?)\\]").matcher(name)).find()) {
			r.putAll(getAliases(m.replaceFirst("$1"), value, variations));
			r.putAll(getAliases(m.replaceFirst("").replace("  ", " "), value, variations));
		} else if ((m = Pattern.compile("\\(([^\\(\\)]+?)\\)").matcher(name)).find()) {
			final String[] split = m.group(1).split("\\|");
			if (split.length == 1) {
				Skript.error(m_brackets_error.toString());
			}
			for (final String s : split) {
				r.putAll(getAliases(m.replaceFirst(s), value, variations));
			}
		} else if ((m = Pattern.compile("\\{(.+?)\\}").matcher(name)).find()) {
			if (variations.get(m.group(1)) != null) {
				boolean hasDefault = false;
				for (final Entry<String, ItemType> v : variations.get(m.group(1)).entrySet()) {
					String n;
					if (v.getKey().equalsIgnoreCase("{default}")) {
						hasDefault = true;
						n = m.replaceFirst("").replace("  ", " ");
					} else {
						n = m.replaceFirst(v.getKey());
					}
					final ItemType t = v.getValue().intersection(value);
					if (t != null)
						r.putAll(getAliases(n, t, variations));
					else
						Skript.warning(m_empty_alias.toString(n));
				}
				if (!hasDefault)
					r.putAll(getAliases(m.replaceFirst("").replace("  ", " "), value, variations));
			} else {
				Skript.error(m_unknown_variation.toString(m.group(1)));
			}
		} else {
			r.put(name, value);
		}
		return r;
	}
	
	/**
	 * Parses & adds new aliases
	 * 
	 * @param name mixedcase string
	 * @param value
	 * @param variations
	 * @return amount of added aliases
	 */
	static int addAliases(final String name, final String value, final HashMap<String, HashMap<String, ItemType>> variations) {
		final ItemType t = parseAlias(value);
		if (t == null) {
			return 0;
		}
		final HashMap<String, ItemType> as = getAliases(name, t, variations);
		boolean printedStartingWithNumberError = false;
//		boolean printedSyntaxError = false;
		for (final Entry<String, ItemType> e : as.entrySet()) {
			final String s = e.getKey().trim().replaceAll("\\s+", " ");
			final Pair<String, String> p = Language.getPlural(s);
			final String lcs = p.first.toLowerCase();
			final String lcp = p.second.toLowerCase();
			if (lcs.matches("\\d+ .*") || lcp.matches("\\d+ .*")) {
				if (!printedStartingWithNumberError) {
					Skript.error(m_starting_with_number.toString());
					printedStartingWithNumberError = true;
				}
				continue;
			}
//			if (lc.contains(",") || lc.contains(" and ") || lc.contains(" or ")) {
//				if (!printedSyntaxError) {
//					Skript.error("aliases must not contain syntax elements (comma, 'and', 'or')");
//					printedSyntaxError = true;
//				}
//				continue;
//			}
			boolean b;
			if ((b = lcs.endsWith(" " + itemSingular)) || lcp.endsWith(" " + itemPlural)) {
				final ItemType i = aliases.get((b ? lcs : lcp).substring(0, (b ? lcs : lcp).length() - (b ? itemSingular.length() : itemPlural.length()) - 1));
				if (i != null)
					i.setItem(e.getValue());
			} else if ((b = lcs.endsWith(" " + blockSingular)) || lcp.endsWith(" " + blockPlural)) {
				final ItemType i = aliases.get((b ? lcs : lcp).substring(0, (b ? lcs : lcp).length() - (b ? blockSingular.length() : blockPlural.length()) - 1));
				if (i != null)
					i.setBlock(e.getValue());
			} else {
				ItemType i = aliases.get(lcs + " " + itemSingular);
				if (i != null)
					e.getValue().setItem(i);
				else
					e.getValue().setItem(aliases.get(lcs + " " + itemPlural));
				i = aliases.get(lcs + " " + blockSingular);
				if (i != null)
					e.getValue().setBlock(i);
				else
					e.getValue().setBlock(aliases.get(lcs + " " + blockPlural));
			}
			aliases.put(lcs, e.getValue());
			aliases.put(lcp, e.getValue());
			
			//if (logSpam()) <- =P
			//	info("added alias " + s + " for " + e.getValue());
			
			if (e.getValue().getTypes().size() == 1) {
				final ItemData d = e.getValue().getTypes().get(0);
				MaterialName n = materialNames.get(Integer.valueOf(d.getId()));
				if (d.dataMin == -1 && d.dataMax == -1) {
					if (n != null) {
						if (n.singular.equals("" + d.getId()) && n.singular.equals(n.plural)) {
							n.singular = p.first;
							n.plural = p.second;
						}
					} else {
						materialNames.put(Integer.valueOf(d.getId()), new MaterialName(d.getId(), p.first, p.second));
					}
				} else {
					if (n == null)
						materialNames.put(Integer.valueOf(d.getId()), n = new MaterialName(d.getId(), "" + d.getId(), "" + d.getId()));
					n.names.put(new Pair<Short, Short>(d.dataMin, d.dataMax), p);
				}
			}
		}
		return as.size();
	}
	
	private final static class MaterialName {
		private String singular;
		private String plural;
		private final int id;
		private final HashMap<Pair<Short, Short>, Pair<String, String>> names = new HashMap<Pair<Short, Short>, Pair<String, String>>();
		
		public MaterialName(final int id, final String singular, final String plural) {
			this.id = id;
			this.singular = singular;
			this.plural = plural;
		}
		
		public String toString(final short dataMin, final short dataMax, final boolean plural) {
			if (names == null)
				return plural ? this.plural : singular;
			Pair<String, String> s = names.get(new Pair<Short, Short>(dataMin, dataMax));
			if (s != null)
				return plural ? s.second : s.first;
			if (dataMin == -1 && dataMax == -1 || dataMin == 0 && dataMax == 0)
				return plural ? this.plural : singular;
			s = names.get(new Pair<Short, Short>((short) -1, (short) -1));
			if (s != null)
				return plural ? s.second : s.first;
			return plural ? this.plural : singular;
		}
		
		public String getDebugName(final short dataMin, final short dataMax, final boolean plural) {
			if (names == null)
				return plural ? this.plural : singular;
			final Pair<String, String> s = names.get(new Pair<Short, Short>(dataMin, dataMax));
			if (s != null)
				return plural ? s.second : s.first;
			if (dataMin == -1 && dataMax == -1 || dataMin == 0 && dataMax == 0)
				return plural ? this.plural : singular;
			return (plural ? this.plural : singular) + ":" + (dataMin == -1 ? 0 : dataMin) + (dataMin == dataMax ? "" : "-" + (dataMax == -1 ? (id <= Skript.MAXBLOCKID ? 15 : Short.MAX_VALUE) : dataMax));
		}
	}
	
	/**
	 * Gets the custom name of of a material, or the default if none is set.
	 * 
	 * @param id
	 * @param data
	 * @return
	 */
	public final static String getMaterialName(final int id, final short data, final boolean plural) {
		return getMaterialName(id, data, data, plural);
	}
	
	public final static String getDebugMaterialName(final int id, final short data, final boolean plural) {
		return getDebugMaterialName(id, data, data, plural);
	}
	
	public final static String getMaterialName(final int id, final short dataMin, final short dataMax, final boolean plural) {
		final MaterialName n = materialNames.get(Integer.valueOf(id));
		if (n == null) {
			return "" + id;
		}
		return n.toString(dataMin, dataMax, plural);
	}
	
	public final static String getDebugMaterialName(final int id, final short dataMin, final short dataMax, final boolean plural) {
		final MaterialName n = materialNames.get(Integer.valueOf(id));
		if (n == null) {
			return "" + id + ":" + dataMin + (dataMax == dataMin ? "" : "-" + dataMax);
		}
		return n.getDebugName(dataMin, dataMax, plural);
	}
	
	/**
	 * @return how many ids are missing an alias, including the 'any id' (-1)
	 */
	final static int addMissingMaterialNames() {
		int r = 0;
		final StringBuilder missing = new StringBuilder(m_missing_aliases + " ");
		for (final Material m : Material.values()) {
			if (materialNames.get(Integer.valueOf(m.getId())) == null) {
				materialNames.put(Integer.valueOf(m.getId()), new MaterialName(m.getId(), m.toString().toLowerCase().replace('_', ' '), m.toString().toLowerCase().replace('_', ' ')));
				missing.append(m.getId() + ", ");
				r++;
			}
		}
		final MaterialName m = materialNames.get(Integer.valueOf(-1));
		if (m == null) {
			materialNames.put(Integer.valueOf(-1), new MaterialName(-1, "anything", "anything"));
			missing.append("<any>, ");
			r++;
		}
		if (r > 0)
			Skript.warning(missing.substring(0, missing.length() - 2));
		return r;
	}
	
	/**
	 * Parses an ItemType to be used as an alias, i.e. it doesn't parse 'all'/'every' and the amount.
	 * 
	 * @param s mixed case string
	 * @return
	 */
	public static ItemType parseAlias(final String s) {
		if (s == null || s.isEmpty()) {
			Skript.error(m_empty_string.toString());
			return null;
		}
		if (s.equals("*"))
			return everything;
		
		final ItemType t = new ItemType();
		
		final String[] types = s.split("\\s*,\\s*");
		for (final String type : types) {
			if (parseType(type, t, true) == null)
				return null;
		}
		
		return t;
	}
	
	private final static RegexMessage p_any = new RegexMessage("aliases.any", "", " (.+)", Pattern.CASE_INSENSITIVE);
	private final static RegexMessage p_every = new RegexMessage("aliases.every", "", " (.+)", Pattern.CASE_INSENSITIVE);
	private final static RegexMessage p_of_every = new RegexMessage("aliases.of every", "(\\d+) ", " (.+)", Pattern.CASE_INSENSITIVE);
	private final static RegexMessage p_of = new RegexMessage("aliases.of", "(\\d+) (?:", " )?(.+)", Pattern.CASE_INSENSITIVE);
	
	/**
	 * Parses an ItemType.<br>
	 * Prints errors.
	 * 
	 * @param s
	 * @return The parsed ItemType or null if the input is invalid.
	 */
	public static ItemType parseItemType(String s) {
		if (s == null || s.isEmpty())
			return null;
		s = s.trim();
		
		final ItemType t = new ItemType();
		
		Matcher m;
		if ((m = p_of_every.getPattern().matcher(s)).matches()) {
			t.setAmount(Utils.parseInt(m.group(1)));
			t.setAll(true);
			s = m.group(m.groupCount());
		} else if ((m = p_of.getPattern().matcher(s)).matches()) {
			t.setAmount(Utils.parseInt(m.group(1)));
			s = m.group(m.groupCount());
		} else if ((m = p_every.getPattern().matcher(s)).matches()) {
			t.setAll(true);
			s = m.group(m.groupCount());
		} else {
			final int l = s.length();
			s = Noun.stripIndefiniteArticle(s);
			if (s.length() != l) // had indefinite article
				t.setAmount(1);
		}
		
		final String lc = s.toLowerCase();
		final String of = Language.getSpaced("enchantments.of").toLowerCase();
		int c = -1;
		outer: while ((c = lc.indexOf(of, c + 1)) != -1) {
			final ItemType t2 = t.clone();
			final RetainingLogHandler log = SkriptLogger.startRetainingLog();
			try {
				if (parseType(s.substring(0, c), t2, false) == null) {
					log.stop();
					continue;
				}
			} finally {
				log.stop();
			}
			if (t2.numTypes() == 0)
				continue;
			final Map<Enchantment, Integer> enchantments = new HashMap<Enchantment, Integer>();
			final String[] enchs = lc.substring(c + of.length(), lc.length()).split("\\s*(,|" + Language.get("and") + ")\\s*");
			for (final String ench : enchs) {
				final EnchantmentType e = EnchantmentType.parse(ench);
				if (e == null)
					continue outer;
				enchantments.put(e.getType(), e.getLevel());
			}
			t2.addEnchantments(enchantments);
			return t2;
		}
		
		if (parseType(s, t, false) == null)
			return null;
		
		if (t.numTypes() == 0)
			return null;
		
		return t;
	}
	
	/**
	 * Prints errors.
	 * 
	 * @param s The string holding the type, can be either a number or an alias, plus an optional data part. Case does not matter.
	 * @param t The ItemType to add the parsed ItemData(s) to (i.e. this ItemType will be modified)
	 * @param isAlias Whether this type is parsed for an alias.
	 * @return The given item type or null if the input couldn't be parsed.
	 */
	private final static ItemType parseType(final String s, final ItemType t, final boolean isAlias) {
		ItemType i;
		int c = s.indexOf(':');
		if (c == -1)
			c = s.length();
		final String type = s.substring(0, c);
		ItemData data = null;
		if (c != s.length()) {
			data = parseData(s.substring(c + 1));
			if (data == null) {
				Skript.error(m_invalid_item_data.toString(s.substring(c)));
				return null;
			}
		}
		if (type.isEmpty()) {
			t.add(data == null ? new ItemData() : data);
			return t;
		} else if (type.matches("\\d+")) {
			ItemData d = new ItemData(Utils.parseInt(type));
			if (Material.getMaterial(d.getId()) == null) {
				Skript.error(m_invalid_id.toString(d.getId()));
				return null;
			}
			if (data != null) {
				if (d.getId() <= Skript.MAXBLOCKID && (data.dataMax > 15 || data.dataMin > 15)) {
					Skript.error(m_invalid_block_data.toString());
					return null;
				}
				d = d.intersection(data);
			}
			if (!isAlias) {
				Skript.warning("Using an ID instead of an alias is discouraged. " +
						(d.toString().equals(type) ?
								"Please crate an alias for '" + type + (type.equals(s) ? "" : " or '" + s + "'") + "' (" + Material.getMaterial(d.getId()).name() + ") in aliases.sk or the script's aliases section and use that instead." :
								"Please replace '" + s + "' with e.g. '" + d.toString(true, false) + "'."));
			}
			t.add(d);
			return t;
		} else if ((i = getAlias(type)) != null) {
			for (ItemData d : i) {
				if (data != null) {
					if (d.getId() <= Skript.MAXBLOCKID && (data.dataMax > 15 || data.dataMin > 15)) {
						Skript.error(m_invalid_block_data.toString());
						return null;
					}
					d = d.intersection(data);
				} else {
					d = d.clone();
				}
				t.add(d);
			}
			if (data == null) {
				if (i.hasItem())
					t.setItem(i.getItem().clone());
				if (i.hasBlock())
					t.setBlock(i.getBlock().clone());
			}
			return t;
		}
		Skript.error(m_invalid_item_type.toString(s));
		return null;
	}
	
	/**
	 * Gets an alias from the aliases defined in the config.
	 * 
	 * @param s The alias to get, case does not matter
	 * @return A copy of the ItemType represented by the given alias or null if no such alias exists.
	 */
	private final static ItemType getAlias(final String s) {
		ItemType i;
		String lc = s.toLowerCase();
		final Matcher m = p_any.getPattern().matcher(lc);
		if (m.matches()) {
			lc = m.group(m.groupCount());
		}
		if ((i = getAlias_i(lc)) != null)
			return i.clone();
//		boolean b;
//		if ((b = lc.endsWith(" " + blockSingular)) || lc.endsWith(" " + blockPlural)) {
//			if ((i = getAlias_i(s.substring(0, s.length() - (b ? blockSingular.length() : blockPlural.length()) - 1))) != null) {
//				i = i.clone();
//				for (int j = 0; j < i.numTypes(); j++) {
//					final ItemData d = i.getTypes().get(j);
//					if (d.getId() > Skript.MAXBLOCKID) {
//						i.remove(d);
//						j--;
//					}
//				}
//				if (i.getTypes().isEmpty())
//					return null;
//				return i;
//			}
//		} else if ((b = lc.endsWith(" " + itemSingular)) || lc.endsWith(" " + itemPlural)) {
//			if ((i = getAlias_i(s.substring(0, s.length() - (b ? itemSingular.length() : itemPlural.length()) - 1))) != null) {
//				for (int j = 0; j < i.numTypes(); j++) {
//					final ItemData d = i.getTypes().get(j);
//					if (d.getId() != -1 && d.getId() <= Skript.MAXBLOCKID) {
//						i.remove(d);
//						j--;
//					}
//				}
//				if (i.getTypes().isEmpty())
//					return null;
//				return i;
//			}
//		}
//		return getAlias_i(lc);
		return null;
	}
	
	/**
	 * Gets the data part of an item data
	 * 
	 * @param s Everything after ':'
	 * @return ItemData with only the dataMin and dataMax set
	 */
	private final static ItemData parseData(final String s) {
		if (s.isEmpty())
			return new ItemData();
		if (!s.matches("\\d+(-\\d+)?"))
			return null;
		final ItemData t = new ItemData();
		int i = s.indexOf('-');
		if (i == -1)
			i = s.length();
		try {
			t.dataMin = Short.parseShort(s.substring(0, i));
			t.dataMax = (i == s.length() ? t.dataMin : Short.parseShort(s.substring(i + 1, s.length())));
		} catch (final NumberFormatException e) { // overflow
			Skript.error(m_out_of_data_range.toString(Short.MAX_VALUE));
			return null;
		}
		if (t.dataMin > t.dataMax) {
			Skript.error(m_invalid_range.toString());
			return null;
		}
		return t;
	}
	
	public static void clear() {
		aliases.clear();
		materialNames.clear();
	}
	
	private static Config aliasConfig;
	
	public static void load() {
		
		try {
			aliasConfig = new Config(new File(Skript.getInstance().getDataFolder(), "aliases.sk"), false, true, "=");
		} catch (final IOException e) {
			Skript.error("Could not load the aliases config: " + e.getLocalizedMessage());
			return;
		}
		
		final ArrayList<String> aliasNodes = new ArrayList<String>();
		
		aliasConfig.validate(
				new SectionValidator()
						.addEntry("aliases", new Setter<String>() {
							@Override
							public void set(final String s) {
								for (final String n : s.split(","))
									aliasNodes.add(n.trim());
							}
						}, false)
						.addEntry("item", new Setter<String>() {
							@Override
							public void set(final String s) {
								final Pair<String, String> p = Language.getPlural(s);
								itemSingular = p.first.toLowerCase();
								itemPlural = p.second.toLowerCase();
							}
						}, false)
						.addEntry("block", new Setter<String>() {
							@Override
							public void set(final String s) {
								final Pair<String, String> p = Language.getPlural(s);
								blockSingular = p.first.toLowerCase();
								blockPlural = p.second.toLowerCase();
							}
						}, false)
						.setAllowUndefinedSections(true));
		
		for (final Node node : aliasConfig.getMainNode()) {
			if (node instanceof SectionNode) {
				if (!aliasNodes.contains(node.getName())) {
					Skript.error(m_invalid_section.toString(node.getName()));
				}
			}
		}
		
		final HashMap<String, HashMap<String, ItemType>> variations = new HashMap<String, HashMap<String, ItemType>>();
		int num = 0;
		for (final String an : aliasNodes) {
			final Node node = aliasConfig.getMainNode().get(an);
			SkriptLogger.setNode(node);
			if (node == null) {
				Skript.error(m_section_not_found.toString(an));
				continue;
			}
			if (!(node instanceof SectionNode)) {
				Skript.error(m_not_a_section.toString(an));
				continue;
			}
			int i = 0;
			for (final Node n : (SectionNode) node) {
				if (n instanceof EntryNode) {
					i += addAliases(((EntryNode) n).getKey(), ((EntryNode) n).getValue(), variations);
				} else if (n instanceof SectionNode) {
					if (!(n.getName().startsWith("{") && n.getName().endsWith("}"))) {
						Skript.error(m_unexpected_non_variation_section.toString());
						continue;
					}
					final HashMap<String, ItemType> vs = new HashMap<String, ItemType>();
					for (final Node a : (SectionNode) n) {
						if (a instanceof SectionNode) {
							Skript.error(m_unexpected_section.toString());
							continue;
						}
						final ItemType t = Aliases.parseAlias(((EntryNode) a).getValue());
						if (t != null)
							vs.put(((EntryNode) a).getKey(), t);
					}
					variations.put(n.getName().substring(1, n.getName().length() - 1), vs);
				}
			}
			if (Skript.logVeryHigh())
				Skript.info(m_loaded_x_aliases_from.toString(i, node.getName()));
			num += i;
		}
		SkriptLogger.setNode(null);
		
		if (Skript.logNormal())
			Skript.info(m_loaded_x_aliases.toString(num));
		
		addMissingMaterialNames();
		
		if (!SkriptConfig.keepConfigsLoaded.value())
			aliasConfig = null;
		
	}
	
}