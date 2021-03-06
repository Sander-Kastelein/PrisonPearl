package com.untamedears.PrisonPearl.managers;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import vg.civcraft.mc.bettershards.BetterShardsAPI;
import vg.civcraft.mc.mercury.MercuryAPI;

import com.untamedears.PrisonPearl.PrisonPearl;
import com.untamedears.PrisonPearl.PrisonPearlPlugin;
import com.untamedears.PrisonPearl.PrisonPearlStorage;
import com.untamedears.PrisonPearl.SaveLoad;
import com.untamedears.PrisonPearl.Summon;
import com.untamedears.PrisonPearl.database.PrisonPearlMysqlStorage;
import com.untamedears.PrisonPearl.events.PrisonPearlEvent;
import com.untamedears.PrisonPearl.events.SummonEvent;

public class SummonManager implements Listener, SaveLoad {
	private final PrisonPearlPlugin plugin;
	private final PrisonPearlStorage pearls;
	private boolean isMysql;
	private PrisonPearlMysqlStorage mysqlDb;
	
	private final Map<UUID, Summon> summons;
	private boolean dirty;
    private final boolean canSpeakDefault;
    private final boolean canDamageDefault;
    private final boolean canBreakDefault;
	
	public SummonManager(PrisonPearlPlugin plugin, PrisonPearlStorage pearls) {
		this.plugin = plugin;
		this.pearls = pearls;
		isMysql = plugin.getPPConfig().getMysqlEnabled();
        canSpeakDefault = plugin.getConfig().getBoolean("can_speak_default", true);
        canDamageDefault = plugin.getConfig().getBoolean("can_damage_default", true);
        canBreakDefault = plugin.getConfig().getBoolean("can_break_default", true);

		summons = new HashMap<UUID, Summon>();
		
		mysqlDb = plugin.getMysqlStorage();
		
		Bukkit.getPluginManager().registerEvents(this, plugin);
		Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
			public void run() {
				inflictSummonDamage();
			}
		}, 0, plugin.getConfig().getInt("summon_damage_ticks"));
	}
	
	public boolean isDirty() {
		return dirty;
	}
	
	public void load(File file) throws NumberFormatException, IOException {
		if (isMysql){
			loadMysql();
			return;
		}
		FileInputStream fis = new FileInputStream(file);
		BufferedReader br = new BufferedReader(new InputStreamReader(fis));
		
		String line;
		while ((line = br.readLine()) != null) {
			String[] parts = line.split(" ");
			String idString = parts[0];
			Location loc = new Location(Bukkit.getWorld(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]), Integer.parseInt(parts[4]));
			int dist = parts.length >= 6 ? Integer.parseInt(parts[5]) : plugin.getConfig().getInt("summon_damage_radius");
            int damage = parts.length >= 7 ? Integer.parseInt(parts[6]) : plugin.getConfig().getInt("summon_damage_amt");
            boolean canSpeak = parts.length >= 8 ? Boolean.parseBoolean(parts[7]) : canSpeakDefault;
            boolean canDamage = parts.length >= 9 ? Boolean.parseBoolean(parts[8]) : canDamageDefault;
            boolean canBreak = parts.length == 10 ? Boolean.parseBoolean(parts[9]) : canBreakDefault;
            System.out.println(idString + " " + loc + " " + dist + " " + damage + " " + canSpeak + " " + canDamage + " " + canBreak);

            UUID id = UUID.fromString(idString);
			
			if (!pearls.isImprisoned(id))
				continue;
			
			summons.put(id, new Summon(id, loc, dist, damage, canSpeak, canDamage, canBreak));
		}
		
		fis.close();
		dirty = false;
	}
	
	public void save(File file) throws IOException {
		if (isMysql){
			saveMysql();
			return;
		}
		FileOutputStream fos = new FileOutputStream(file);
		BufferedWriter br = new BufferedWriter(new OutputStreamWriter(fos));
		
		for (Entry<UUID, Summon> entry : summons.entrySet()) {
			Summon summon = entry.getValue();
			Location loc = summon.getReturnLocation();
			br.append(summon.getSummonedId().toString()).append(" ").append(loc.getWorld().getName()).append(" ").append(String.valueOf(loc.getBlockX())).append(" ").append(String.valueOf(loc.getBlockY())).append(" ").append(String.valueOf(loc.getBlockZ())).append(" ").append(String.valueOf(summon.getAllowedDistance())).append(" ").append(String.valueOf(summon.getDamageAmount())).append(" ").append(String.valueOf(summon.isCanSpeak())).append(" ").append(String.valueOf(summon.isCanDealDamage())).append(" ").append(String.valueOf(summon.isCanBreakBlocks())).append("\n");
		}
		
		br.flush();
		fos.close();
		dirty = false;
	}
	
	private void inflictSummonDamage() {
        Map<Player, Double> inflictDmg = new HashMap<Player, Double>();
		Iterator<Entry<UUID, Summon>> i = summons.entrySet().iterator();
		while (i.hasNext()) {
			Summon summon = i.next().getValue();
			PrisonPearl pp = pearls.getByImprisoned(summon.getSummonedId());
			if (pp == null) {
				System.err.println("Somehow " + summon.getSummonedId() + " was summoned but isn't imprisoned");
				i.remove();
				dirty = true;
				continue;
			}
			
			Player player = pp.getImprisonedPlayer();
			if (player == null)
				continue;
			
			Location pploc = pp.getLocation();
			Location playerloc = player.getLocation();
			
			if (pploc.getWorld() != playerloc.getWorld() || pploc.distance(playerloc) > summon.getAllowedDistance()) {
                inflictDmg.put(player, (double)summon.getDamageAmount());
            }
		}
        for (Map.Entry<Player, Double> entry : inflictDmg.entrySet()) {
            final Player player = entry.getKey();
            final Double damage = entry.getValue();
			player.damage(damage);
        }
	}
	
	// Only will be used by sharding.
	public void addSummonPearl(PrisonPearl pp) {
		Summon summon = new Summon(pp.getImprisonedId(), null, plugin.getConfig().getInt("summon_damage_radius"), plugin.getConfig().getInt("summon_damage_amt"), canSpeakDefault, canDamageDefault, canBreakDefault);
		summons.put(summon.getSummonedId(), summon);
	}
	
	public boolean summonPearl(PrisonPearl pp) {
		Player player = pp.getImprisonedPlayer();
		// Start here for mercury sharding 
		if (player == null && plugin.isBetterShardsEnabled() && plugin.isMercuryLoaded()) {
			UUID uuid = pp.getImprisonedId();
			if (summons.containsKey(uuid) || !MercuryAPI.getAllAccounts().contains(uuid))
				return false;
			
			Summon summon = new Summon(uuid, null, plugin.getConfig().getInt("summon_damage_radius"), plugin.getConfig().getInt("summon_damage_amt"), canSpeakDefault, canDamageDefault, canBreakDefault);
			plugin.checkToSummon.add(uuid);
			summons.put(summon.getSummonedId(), summon);
			
			if (!summonEvent(pp, SummonEvent.Type.SUMMONED, pp.getLocation())) {
				summons.remove(uuid);
				return false;
			}
			return true;
		}
		if (player == null || player.isDead())
			return false;
		
		if (summons.containsKey(player.getUniqueId()))
			return false;
		
		Summon summon = new Summon(player.getUniqueId(), player.getLocation().add(0, -.5, 0), plugin.getConfig().getInt("summon_damage_radius"), plugin.getConfig().getInt("summon_damage_amt"), canSpeakDefault, canDamageDefault, canBreakDefault);
		summons.put(summon.getSummonedId(), summon);
		if(isMysql)
			mysqlDb.addSummonedPlayer(summon);
		
		if (!summonEvent(pp, SummonEvent.Type.SUMMONED, pp.getLocation())) {
			summons.remove(player.getUniqueId());
			return false;
		}
		
		dirty = true;
		return true;
	}
	
	public boolean returnPearl(PrisonPearl pp) {
		Summon summon = summons.remove(pp.getImprisonedId());
		if (summon == null)
			return false;
		
		if (!summonEvent(pp, SummonEvent.Type.RETURNED, summon.getReturnLocation())) {
			summons.put(pp.getImprisonedId(), summon);
			return false;
		}
		
		if (isMysql)
			mysqlDb.removeSummonedPlayer(summon);
		
		dirty = true;
		return true;
	}
	
	public boolean killPearl(PrisonPearl pp) {
		Summon summon = summons.remove(pp.getImprisonedId());
		if (summon == null)
			return false;
		
		if (!summonEvent(pp, SummonEvent.Type.KILLED, summon.getReturnLocation())) {
			summons.put(pp.getImprisonedId(), summon);
			return false;
		}
		
		pp.getImprisonedPlayer().setHealth(0.0);
		dirty = true;
		
		if (isMysql)
			mysqlDb.removeSummonedPlayer(summon);
		
		return true;
	}
	
	public boolean isSummoned(Player player) {
		return summons.containsKey(player.getUniqueId());
	}
	
	public boolean isSummoned(PrisonPearl pp) {
		return summons.containsKey(pp.getImprisonedId());
	}
	
	public Summon getSummon(Player player) {
		return summons.get(player.getUniqueId());
	}
	
	public Summon getSummon(UUID id) {
		return summons.get(id);
	}
	
	@EventHandler(priority=EventPriority.HIGHEST)
	public void onEntityDeath(EntityDeathEvent event) {
		if (!(event.getEntity() instanceof Player))
			return;

		Player player = (Player)event.getEntity();
		Summon summon = summons.remove(player.getUniqueId());
		if (summon == null)
			return;
		dirty = true;
		
		PrisonPearl pp = pearls.getByImprisoned(player);
		if (pp == null)
			return;
		
		if (isMysql)
			mysqlDb.removeSummonedPlayer(summon);
		
		summonEvent(pp, SummonEvent.Type.DIED);
	}
	
	@EventHandler(priority=EventPriority.MONITOR)
	public void onPrisonPearlEvent(PrisonPearlEvent event) {
		if (event.getType() == PrisonPearlEvent.Type.FREED) {
			PrisonPearl pp = event.getPrisonPearl();
			UUID uuid = pp.getImprisonedId();
			summons.remove(uuid);
			dirty = true;
		}
	}
	
    private boolean summonEvent(PrisonPearl pp, SummonEvent.Type type) {
		SummonEvent event = new SummonEvent(pp, type);
		Bukkit.getPluginManager().callEvent(event);
		return !event.isCancelled();
	}
	
    private boolean summonEvent(PrisonPearl pp, SummonEvent.Type type, Location loc) {
		SummonEvent event = new SummonEvent(pp, type, loc);
		Bukkit.getPluginManager().callEvent(event);
		return !event.isCancelled();
	}
    
    public void loadMysql(){
    	summons.putAll(mysqlDb.getAllSummonedPlayers());
    }
    
    public void saveMysql(){
    	for(Summon s: summons.values())
    		mysqlDb.updateSummonedPlayer(s);
    }
}
