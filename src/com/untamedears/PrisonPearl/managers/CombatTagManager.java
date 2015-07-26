package com.untamedears.PrisonPearl.managers;

import java.util.logging.Logger;

import net.minelink.ctplus.CombatTagPlus;
import net.minelink.ctplus.Npc;
import net.minelink.ctplus.compat.api.NpcIdentity;
import net.minelink.ctplus.compat.api.NpcPlayerHelper;

import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import com.trc202.CombatTag.CombatTag;
import com.trc202.CombatTagApi.CombatTagApi;


public class CombatTagManager {
	private CombatTagApi combatTagApi;
	private NpcPlayerHelper combatTagPlusApi;
	private boolean combatTagEnabled = false;
	private boolean combatTagPlusEnabled = false;

	public CombatTagManager(Server server, Logger l) {
		if(server.getPluginManager().getPlugin("CombatTag") != null) {
			combatTagApi = new CombatTagApi((CombatTag)server.getPluginManager().getPlugin("CombatTag"));
			combatTagEnabled = true;
		}
		
		if (server.getPluginManager().getPlugin("CombatTagPlus") != null){
			combatTagPlusApi = ((CombatTagPlus) server.getPluginManager().getPlugin("CombatTagPlus")).getNpcPlayerHelper();
			combatTagPlusEnabled = true;
		}
	}
	
	public boolean isCombatTagNPC(Entity player) {
        return combatTagEnabled && combatTagApi != null && combatTagApi.isNPC(player);
    }
	
	public boolean isCombatTagPlusNPC(Player player) {
		return combatTagPlusEnabled && combatTagPlusApi.isNpc(player);
	}
	
	public boolean isCombatTagged(Player player) {
        return combatTagEnabled && combatTagApi != null && combatTagApi.isInCombat(player.getName());
    }
	
	public boolean isCombatTagged(String playerName) {
		return combatTagEnabled && combatTagApi != null && combatTagApi.isInCombat(playerName);
	}
	
	public NpcIdentity getCombatTagPlusNPCIdentity(Player player){
		return combatTagPlusApi.getIdentity(player);
	}
	
//	public String getNPCPlayerName(Entity player) {
//		if (combatTagEnabled && combatTagApi != null) {
//			if (combatTagApi.isNPC(player)) {
//				return plugin.getPlayerName(player);
//			}
//		}
//		return "";
//	}
}
