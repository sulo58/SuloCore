package com.sulo.core.features;

import com.sulo.core.SuloCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scoreboard.*;
import org.bukkit.ChatColor;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class CoreFeatures implements CommandExecutor, Listener {
    private final SuloCore plugin;
    private final File file;
    private final YamlConfiguration data;
    private final Map<UUID, UUID> tpa = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> duels = new ConcurrentHashMap<>();
    private final Map<UUID, Location> lastLocations = new ConcurrentHashMap<>();
    private final Map<UUID, Long> chatTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playStarts = new ConcurrentHashMap<>();
    private final Set<UUID> muted = ConcurrentHashMap.newKeySet();
    private final Set<UUID> jailed = ConcurrentHashMap.newKeySet();
    private boolean maintenance;

    public CoreFeatures(SuloCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        this.maintenance = data.getBoolean("maintenance", false);
        for (String id : data.getStringList("muted")) try { muted.add(UUID.fromString(id)); } catch (IllegalArgumentException ignored) {}
        for (String id : data.getStringList("jailed")) try { jailed.add(UUID.fromString(id)); } catch (IllegalArgumentException ignored) {}
        for (String name : List.of("bal","balance","pay","market","stats","daily","quest","xp","tpa","tpaccept","tpdeny","sethome","home","delhome","setwarp","warp","delwarp","setspawn","spawn","back","kick","ban","mute","unmute","warn","jail","unjail","crate","key","coinflip","duel","duelaccept","report","maintenance","announce","sulocore")) {
            PluginCommand command = plugin.getCommand(name);
            if (command != null) command.setExecutor(this);
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::autoMessages, 20L * 60 * 5, 20L * 60 * 5);
    }

    private void save() {
        data.set("muted", muted.stream().map(UUID::toString).toList());
        data.set("jailed", jailed.stream().map(UUID::toString).toList());
        data.set("maintenance", maintenance);
        try { data.save(file); } catch (IOException ex) { plugin.getLogger().warning("data.yml save failed: " + ex.getMessage()); }
    }
    private String key(Player p) { return "players." + p.getUniqueId(); }
    private double balance(Player p) { return data.getDouble(key(p) + ".balance", 0); }
    private void balance(Player p, double amount) { data.set(key(p) + ".balance", amount); save(); }
    private void msg(CommandSender s, String m) { s.sendMessage(ChatColor.translateAlternateColorCodes('&', "&8[&aSuloCore&8] &r" + m)); }
    private boolean playerOnly(CommandSender s) { if (s instanceof Player) return false; msg(s, "&cBu komut sadece oyuncular içindir."); return true; }
    private boolean admin(CommandSender s) { if (s.hasPermission("sulocore.admin")) return true; msg(s, "&cYetkin yok."); return false; }
    private Location readLocation(String path) {
        String world = data.getString(path + ".world");
        if (world == null || Bukkit.getWorld(world) == null) return null;
        return new Location(Bukkit.getWorld(world), data.getDouble(path+".x"), data.getDouble(path+".y"), data.getDouble(path+".z"), (float)data.getDouble(path+".yaw"), (float)data.getDouble(path+".pitch"));
    }
    private void writeLocation(String path, Location l) {
        data.set(path+".world", l.getWorld().getName()); data.set(path+".x", l.getX()); data.set(path+".y", l.getY()); data.set(path+".z", l.getZ()); data.set(path+".yaw", l.getYaw()); data.set(path+".pitch", l.getPitch()); save();
    }
    private Player online(String name) { return Bukkit.getPlayerExact(name); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] a) {
        String cmd = label.toLowerCase(Locale.ROOT);
        if (cmd.equals("sulocore")) {
            if (!admin(sender)) return true;
            if (a.length == 0 || a[0].equalsIgnoreCase("info")) { msg(sender, "&aSuloCore " + plugin.getDescription().getVersion() + " &7| /sulocore status"); return true; }
            if (a[0].equalsIgnoreCase("status")) { msg(sender, "&7MySQL: " + (plugin.getDatabaseManager().isConnected() ? "&aBağlı" : "&cBağlı değil")); return true; }
            if (a[0].equalsIgnoreCase("reload")) { plugin.reloadConfig(); msg(sender, "&aConfig yenilendi. Veri dosyası yeniden yüklenmedi."); return true; }
            msg(sender, "&cKullanım: /sulocore [info|status|reload]"); return true;
        }
        if (cmd.equals("bal") || cmd.equals("balance")) {
            if (playerOnly(sender)) return true;
            Player p=(Player)sender; Player target=a.length>0?online(a[0]):p;
            if(target==null){msg(sender,"&cOyuncu çevrimiçi değil.");return true;}
            msg(sender,"&e"+target.getName()+" bakiyesi: &a"+String.format(Locale.US,"%.2f",balance(target))); return true;
        }
        if (cmd.equals("pay")) {
            if(playerOnly(sender))return true; if(a.length<2){msg(sender,"&c/pay <oyuncu> <miktar>");return true;}
            Player p=(Player)sender,t=online(a[0]); if(t==null||t.equals(p)){msg(sender,"&cGeçerli bir oyuncu seç.");return true;}
            double n; try{n=Double.parseDouble(a[1]);}catch(NumberFormatException ex){msg(sender,"&cGeçersiz miktar.");return true;}
            if(!Double.isFinite(n)||n<=0||balance(p)<n){msg(sender,"&cMiktar geçersiz veya bakiyen yetersiz.");return true;}
            balance(p,balance(p)-n);balance(t,balance(t)+n);msg(p,"&a"+t.getName()+" oyuncusuna "+n+" gönderdin.");msg(t,"&a"+p.getName()+" sana "+n+" gönderdi.");return true;
        }
        if(cmd.equals("market")) { if(playerOnly(sender))return true; openMarket((Player)sender); return true; }
        if(cmd.equals("stats")) { if(playerOnly(sender))return true; Player p=(Player)sender; msg(p,"&eİstatistiklerin: &7Öldürme "+data.getInt(key(p)+".kills")+" | Ölüm "+data.getInt(key(p)+".deaths")+" | XP "+data.getLong(key(p)+".xp")+" | Seviye "+data.getInt(key(p)+".level",1)); return true; }
        if(cmd.equals("daily")) { if(playerOnly(sender))return true; Player p=(Player)sender; long today=System.currentTimeMillis()/86400000L; if(data.getLong(key(p)+".daily",-1)==today){msg(p,"&cGünlük ödülünü zaten aldın.");return true;} data.set(key(p)+".daily",today);balance(p,balance(p)+100);msg(p,"&aGünlük ödülün: 100 coin!");return true; }
        if(cmd.equals("quest")) { if(playerOnly(sender))return true; Player p=(Player)sender; msg(p,"&eGünlük görev: 10 blok kır. İlerleme: "+data.getInt(key(p)+".quest.blocks")+"/10");return true; }
        if(cmd.equals("xp")) { if(playerOnly(sender))return true; Player p=(Player)sender; long xp=data.getLong(key(p)+".xp"); if(a.length>0&&admin(sender)){try{xp+=Long.parseLong(a[0]);data.set(key(p)+".xp",Math.max(0,xp));save();}catch(NumberFormatException ignored){}} msg(p,"&eXP: "+data.getLong(key(p)+".xp")+" | Seviye: "+data.getInt(key(p)+".level",1));return true; }
        if(cmd.equals("tpa")) { if(playerOnly(sender))return true; if(a.length<1){msg(sender,"&c/tpa <oyuncu>");return true;} Player p=(Player)sender,t=online(a[0]);if(t==null||t.equals(p)){msg(p,"&cOyuncu bulunamadı.");return true;}tpa.put(t.getUniqueId(),p.getUniqueId());msg(t,"&e"+p.getName()+" sana ışınlanmak istiyor. /tpaccept veya /tpdeny");msg(p,"&aIşınlanma isteği gönderildi.");return true; }
        if(cmd.equals("tpaccept")||cmd.equals("tpdeny")) { if(playerOnly(sender))return true; Player p=(Player)sender;UUID id=tpa.remove(p.getUniqueId());if(id==null){msg(p,"&cBekleyen istek yok.");return true;}Player from=Bukkit.getPlayer(id);if(from==null){msg(p,"&cOyuncu artık çevrimiçi değil.");return true;}if(cmd.equals("tpaccept")){lastLocations.put(from.getUniqueId(),from.getLocation());from.teleport(p.getLocation());msg(from,"&aIşınlandın.");msg(p,"&aİstek kabul edildi.");}else{msg(from,"&cIşınlanma isteğin reddedildi.");}return true; }
        if(cmd.equals("sethome")||cmd.equals("home")||cmd.equals("delhome")) { if(playerOnly(sender))return true;Player p=(Player)sender;String name=a.length>0?a[0].toLowerCase(Locale.ROOT):"default";String path=key(p)+".homes."+name;if(cmd.equals("sethome")){writeLocation(path,p.getLocation());msg(p,"&aEv kaydedildi: "+name);}else if(cmd.equals("delhome")){data.set(path,null);save();msg(p,"&aEv silindi.");}else{Location l=readLocation(path);if(l==null)msg(p,"&cEv bulunamadı.");else{lastLocations.put(p.getUniqueId(),p.getLocation());p.teleport(l);msg(p,"&aEvine ışınlandın.");}}return true; }
        if(cmd.equals("setwarp")||cmd.equals("warp")||cmd.equals("delwarp")) { if(cmd.equals("setwarp")||cmd.equals("delwarp")){if(!admin(sender))return true;}if(cmd.equals("warp")&&playerOnly(sender))return true;if(a.length<1){msg(sender,"&c/"+cmd+" <isim>");return true;}String path="warps."+a[0].toLowerCase(Locale.ROOT);if(cmd.equals("setwarp")){if(!(sender instanceof Player p)){msg(sender,"&cOyuncu olmalısın.");return true;}writeLocation(path,p.getLocation());msg(p,"&aWarp kaydedildi.");}else if(cmd.equals("delwarp")){data.set(path,null);save();msg(sender,"&aWarp silindi.");}else{Location l=readLocation(path);if(l==null)msg(sender,"&cWarp bulunamadı.");else{Player p=(Player)sender;lastLocations.put(p.getUniqueId(),p.getLocation());p.teleport(l);msg(p,"&aWarp'a ışınlandın.");}}return true; }
        if(cmd.equals("setspawn")){if(!admin(sender))return true;if(!(sender instanceof Player p)){msg(sender,"&cOyuncu olmalısın.");return true;}writeLocation("spawn",p.getLocation());msg(p,"&aSpawn kaydedildi.");return true;}
        if(cmd.equals("spawn")){if(playerOnly(sender))return true;Player p=(Player)sender;Location l=readLocation("spawn");if(l==null)msg(p,"&cSpawn ayarlanmamış.");else{lastLocations.put(p.getUniqueId(),p.getLocation());p.teleport(l);msg(p,"&aSpawn'a ışınlandın.");}return true;}
        if(cmd.equals("back")){if(playerOnly(sender))return true;Player p=(Player)sender;Location l=lastLocations.get(p.getUniqueId());if(l==null)msg(p,"&cDönülecek konum yok.");else p.teleport(l);return true;}
        if(cmd.equals("kick")||cmd.equals("ban")||cmd.equals("mute")||cmd.equals("unmute")||cmd.equals("warn")||cmd.equals("jail")||cmd.equals("unjail")){if(!admin(sender))return true;if(a.length<1){msg(sender,"&c/"+cmd+" <oyuncu> [sebep]");return true;}OfflinePlayer off=Bukkit.getOfflinePlayer(a[0]);Player target=online(a[0]);String reason=a.length>1?String.join(" ",Arrays.copyOfRange(a,1,a.length)):"Yetkili işlemi";if(cmd.equals("kick")){if(target!=null)target.kickPlayer(reason);}else if(cmd.equals("ban")){Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(a[0],reason,null,sender.getName());if(target!=null)target.kickPlayer(reason);}else if(cmd.equals("mute")){muted.add(off.getUniqueId());save();}else if(cmd.equals("unmute")){muted.remove(off.getUniqueId());save();}else if(cmd.equals("jail")){jailed.add(off.getUniqueId());save();if(target!=null)target.teleport(readLocation("jail"));}else if(cmd.equals("unjail")){jailed.remove(off.getUniqueId());save();}else{List<String>w=data.getStringList("warnings."+off.getUniqueId());w.add(reason);data.set("warnings."+off.getUniqueId(),w);save();}msg(sender,"&aİşlem uygulandı.");return true;}
        if(cmd.equals("crate")||cmd.equals("key")){if(playerOnly(sender))return true;Player p=(Player)sender;if(cmd.equals("key")){if(!admin(sender))return true;if(a.length<1){msg(p,"&c/key <oyuncu> [adet]");return true;}Player t=online(a[0]);if(t==null)return true;int n=1;try{if(a.length>1)n=Integer.parseInt(a[1]);}catch(NumberFormatException ignored){}data.set(key(t)+".keys",data.getInt(key(t)+".keys")+Math.max(1,n));save();msg(t,"&aKasa anahtarı aldın.");}else{int keys=data.getInt(key(p)+".keys");if(keys<1){msg(p,"&cAnahtarın yok.");return true;}data.set(key(p)+".keys",keys-1);balance(p,balance(p)+new Random().nextInt(251));msg(p,"&aKasa açıldı! Ödül bakiyene eklendi.");}return true;}
        if(cmd.equals("coinflip")){if(playerOnly(sender))return true;if(a.length<1){msg(sender,"&c/coinflip <miktar>");return true;}Player p=(Player)sender;double bet;try{bet=Double.parseDouble(a[0]);}catch(NumberFormatException ex){return true;}if(bet<=0||balance(p)<bet){msg(p,"&cGeçersiz bahis.");return true;}balance(p,balance(p)-bet);if(new Random().nextBoolean()){balance(p,balance(p)+bet*2);msg(p,"&aKazandın!");}else msg(p,"&cKaybettin.");return true;}
        if(cmd.equals("duel")||cmd.equals("duelaccept")){if(playerOnly(sender))return true;Player p=(Player)sender;if(cmd.equals("duel")){if(a.length<1)return true;Player t=online(a[0]);if(t==null||t.equals(p))return true;duels.put(t.getUniqueId(),p.getUniqueId());msg(t,"&e"+p.getName()+" düello istiyor. /duelaccept");msg(p,"&aDüello isteği gönderildi.");}else{UUID id=duels.remove(p.getUniqueId());Player t=id==null?null:Bukkit.getPlayer(id);if(t==null){msg(p,"&cBekleyen düello yok.");return true;}Location l=readLocation("arena");if(l==null){msg(p,"&cArena ayarlanmamış. Yetkili /setarena konumunu eklemeli.");return true;}lastLocations.put(p.getUniqueId(),p.getLocation());lastLocations.put(t.getUniqueId(),t.getLocation());p.teleport(l);t.teleport(l);msg(p,"&aDüello başladı!");}return true;}
        if(cmd.equals("report")){if(playerOnly(sender))return true;if(a.length<2){msg(sender,"&c/report <oyuncu> <sebep>");return true;}List<String> reports=data.getStringList("reports");reports.add(sender.getName()+" -> "+a[0]+": "+String.join(" ",Arrays.copyOfRange(a,1,a.length)));data.set("reports",reports);save();msg(sender,"&aRapor gönderildi.");return true;}
        if(cmd.equals("maintenance")){if(!admin(sender))return true;maintenance=!maintenance;save();msg(sender,"&eBakım modu: "+(maintenance?"Açık":"Kapalı"));return true;}
        if(cmd.equals("announce")){if(!admin(sender))return true;if(a.length==0)return true;Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',"&6[DUYURU] &f"+String.join(" ",a)));return true;}
        return false;
    }

    private void openMarket(Player p) {
        Inventory inv=Bukkit.createInventory(null,27,ChatColor.GREEN+"SuloCore Market");
        addItem(inv,Material.BREAD,"Ekmek","50",10);addItem(inv,Material.IRON_INGOT,"Demir","150",13);addItem(inv,Material.DIAMOND,"Elmas","500",16);
        p.openInventory(inv);
    }
    private void addItem(Inventory inv,Material type,String name,String price,int slot){ItemStack item=new ItemStack(type);ItemMeta meta=item.getItemMeta();meta.setDisplayName(ChatColor.YELLOW+name);meta.setLore(List.of(ChatColor.GRAY+"Fiyat: "+price+" coin",ChatColor.GREEN+"Satın almak için tıkla"));item.setItemMeta(meta);inv.setItem(slot,item);}
    @EventHandler public void onInventory(InventoryClickEvent e){if(!(e.getWhoClicked() instanceof Player p)||!e.getView().getTitle().equals(ChatColor.GREEN+"SuloCore Market"))return;e.setCancelled(true);ItemStack item=e.getCurrentItem();if(item==null||!item.hasItemMeta())return;String name=ChatColor.stripColor(item.getItemMeta().getDisplayName());double price=switch(name){case "Ekmek"->50;case "Demir"->150;case "Elmas"->500;default->-1;};if(price<0)return;if(balance(p)<price){msg(p,"&cYeterli paran yok.");return;}balance(p,balance(p)-price);p.getInventory().addItem(new ItemStack(item.getType()));msg(p,"&aSatın alındı: "+name);}
    @EventHandler public void onJoin(PlayerJoinEvent e){Player p=e.getPlayer();playStarts.put(p.getUniqueId(),System.currentTimeMillis());if(maintenance&&!p.hasPermission("sulocore.admin")){p.kickPlayer("Sunucu bakım modunda.");return;}if(!p.hasPlayedBefore())Bukkit.broadcastMessage(ChatColor.GREEN+"Aramıza hoş geldin, "+p.getName()+"!");}
    @EventHandler public void onQuit(PlayerQuitEvent e){UUID id=e.getPlayer().getUniqueId();Long start=playStarts.remove(id);if(start!=null)data.set(key(e.getPlayer())+".playtime",data.getLong(key(e.getPlayer())+".playtime")+(System.currentTimeMillis()-start)/1000);save();}
    @EventHandler public void onChat(AsyncPlayerChatEvent e){Player p=e.getPlayer();if(muted.contains(p.getUniqueId())||jailed.contains(p.getUniqueId())){e.setCancelled(true);return;}long now=System.currentTimeMillis();Long last=chatTimes.put(p.getUniqueId(),now);if(last!=null&&now-last<1000){e.setCancelled(true);p.sendMessage(ChatColor.RED+"Çok hızlı mesaj gönderiyorsun.");return;}String text=e.getMessage().toLowerCase(Locale.ROOT);for(String word:plugin.getConfig().getStringList("chat-filter.words"))if(!word.isBlank()&&text.contains(word.toLowerCase(Locale.ROOT))){e.setCancelled(true);p.sendMessage(ChatColor.RED+"Mesajın sohbet filtresine takıldı.");return;}}
    @EventHandler public void onDeath(PlayerDeathEvent e){Player p=e.getEntity();data.set(key(p)+".deaths",data.getInt(key(p)+".deaths")+1);if(p.getKiller()!=null){Player k=p.getKiller();data.set(key(k)+".kills",data.getInt(key(k)+".kills")+1);data.set(key(k)+".xp",data.getLong(key(k)+".xp")+10);}}
    @EventHandler public void onBlockBreak(org.bukkit.event.block.BlockBreakEvent e){Player p=e.getPlayer();String k=key(p)+".quest.blocks";data.set(k,data.getInt(k)+1);if(data.getInt(k)==10){balance(p,balance(p)+200);msg(p,"&aGörev tamamlandı! +200 coin");data.set(k,0);}}
    private void autoMessages(){List<String> messages=plugin.getConfig().getStringList("automation.messages");if(messages.isEmpty())return;String m=messages.get(new Random().nextInt(messages.size()));Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',"&8[&aBilgi&8] &f"+m));}
}
