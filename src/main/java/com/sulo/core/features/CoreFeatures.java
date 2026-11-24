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
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
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
    private final Map<UUID, Long> tpaCreated = new ConcurrentHashMap<>();
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
        for (String name : List.of("bal","balance","baltop","pay","market","stats","daily","quest","xp","tpa","tpaccept","tpdeny","sethome","home","delhome","setwarp","warp","delwarp","setspawn","spawn","back","kick","ban","mute","unmute","warn","jail","unjail","crate","key","coinflip","duel","duelaccept","setarena","setjail","report","maintenance","announce","sulocore")) {
            PluginCommand command = plugin.getCommand(name);
            if (command != null) command.setExecutor(this);
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::autoMessages, 20L * 60 * 5, 20L * 60 * 5);
        Bukkit.getScheduler().runTaskTimer(plugin, this::expireTeleportRequests, 20L, 20L);
    }

    private void expireTeleportRequests() {
        long now = System.currentTimeMillis();
        long timeout = Math.max(10, plugin.getConfig().getLong("teleport.request-timeout-seconds", 60)) * 1000L;
        for (Map.Entry<UUID, Long> entry : tpaCreated.entrySet()) {
            if (now - entry.getValue() >= timeout && tpaCreated.remove(entry.getKey(), entry.getValue())) {
                UUID requester = tpa.remove(entry.getKey());
                Player target = Bukkit.getPlayer(entry.getKey());
                Player from = requester == null ? null : Bukkit.getPlayer(requester);
                if (target != null) msg(target, "&7Işınlanma isteğinin süresi doldu.");
                if (from != null) msg(from, "&cIşınlanma isteğinin süresi doldu.");
            }
        }
    }

    private void save() {
        data.set("muted", muted.stream().map(UUID::toString).toList());
        data.set("jailed", jailed.stream().map(UUID::toString).toList());
        data.set("maintenance", maintenance);
        try { data.save(file); } catch (IOException ex) { plugin.getLogger().warning("data.yml save failed: " + ex.getMessage()); }
    }
    private String key(Player p) { return "players." + p.getUniqueId(); }
    private double balance(Player p) { return data.getDouble(key(p) + ".balance", 0); }
    private double balance(UUID id) { return data.getDouble("players." + id + ".balance", 0); }
    private void balance(UUID id, double amount) { data.set("players." + id + ".balance", amount); save(); }
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
            if (a[0].equalsIgnoreCase("reload")) { plugin.reloadConfig(); plugin.getLanguageManager().reload(); msg(sender, "&aConfig ve dil dosyası yenilendi. Aktif oyuncu verileri yeniden başlatma olmadan yüklenmez."); return true; }
            msg(sender, "&cKullanım: /sulocore [info|status|reload]"); return true;
        }
        if (cmd.equals("bal") || cmd.equals("balance")) {
            if (playerOnly(sender)) return true;
            Player p=(Player)sender; Player target=a.length>0?online(a[0]):p;
            if(target==null){msg(sender,"&cOyuncu çevrimiçi değil.");return true;}
            msg(sender,"&e"+target.getName()+" bakiyesi: &a"+String.format(Locale.US,"%.2f",balance(target))); return true;
        }
        if (cmd.equals("baltop")) { showBalanceTop(sender); return true; }
        if (cmd.equals("pay")) {
            if(playerOnly(sender))return true; if(a.length<2){msg(sender,"&c/pay <oyuncu> <miktar>");return true;}
            Player p=(Player)sender,t=online(a[0]); if(t==null||t.equals(p)){msg(sender,"&cGeçerli bir oyuncu seç.");return true;}
            double n; try{n=Double.parseDouble(a[1]);}catch(NumberFormatException ex){msg(sender,"&cGeçersiz miktar.");return true;}
            if(!Double.isFinite(n)||n<=0||balance(p)<n){msg(sender,"&cMiktar geçersiz veya bakiyen yetersiz.");return true;}
            balance(p,balance(p)-n);balance(t,balance(t)+n);msg(p,"&a"+t.getName()+" oyuncusuna "+n+" gönderdin.");msg(t,"&a"+p.getName()+" sana "+n+" gönderdi.");return true;
        }
        if(cmd.equals("market")) {
            if(playerOnly(sender))return true;
            Player p=(Player)sender;
            if(a.length==0 || a[0].equalsIgnoreCase("browse")) { openMarket(p); return true; }
            if(a[0].equalsIgnoreCase("sell")) {
                if(a.length<2){msg(p,"&cKullanım: /market sell <fiyat>");return true;}
                double price; try{price=Double.parseDouble(a[1]);}catch(NumberFormatException ex){msg(p,"&cGeçersiz fiyat.");return true;}
                ItemStack hand=p.getInventory().getItemInMainHand();
                if(!Double.isFinite(price)||price<=0||hand.getType()==Material.AIR){msg(p,"&cElinde satılacak bir eşya tut ve geçerli fiyat gir.");return true;}
                int id=data.getInt("market.next-id",1);
                data.set("market.next-id",id+1);
                String path="market.listings."+id;
                data.set(path+".seller",p.getUniqueId().toString());
                data.set(path+".seller-name",p.getName());
                data.set(path+".price",price);
                data.set(path+".item",hand.clone());
                p.getInventory().setItemInMainHand(null);
                save(); msg(p,"&aEşyan pazara #"+id+" ilan numarasıyla "+String.format(Locale.US,"%.2f",price)+" coin fiyatına konuldu.");return true;
            }
            if(a[0].equalsIgnoreCase("remove")) {
                if(a.length<2){msg(p,"&cKullanım: /market remove <ilan-id>");return true;}
                int id;try{id=Integer.parseInt(a[1]);}catch(NumberFormatException ex){msg(p,"&cGeçersiz ilan numarası.");return true;}
                String path="market.listings."+id; String owner=data.getString(path+".seller");
                if(owner==null){msg(p,"&cBu ilan bulunamadı.");return true;}
                if(!owner.equals(p.getUniqueId().toString())&&!p.hasPermission("sulocore.admin")){msg(p,"&cSadece kendi ilanını kaldırabilirsin.");return true;}
                ItemStack item=data.getItemStack(path+".item");
                if(item!=null){Map<Integer,ItemStack> left=p.getInventory().addItem(item.clone());left.values().forEach(stack->p.getWorld().dropItemNaturally(p.getLocation(),stack));}
                data.set(path,null);save();msg(p,"&aİlan kaldırıldı ve eşya sana iade edildi.");return true;
            }
            msg(p,"&e/market &7- Pazarı aç\n&e/market sell <fiyat> &7- Elindeki eşyayı satışa koy\n&e/market remove <ilan-id> &7- İlanını kaldır");return true;
        }
        if(cmd.equals("stats")) { if(playerOnly(sender))return true; Player p=(Player)sender; msg(p,"&eİstatistiklerin: &7Öldürme "+data.getInt(key(p)+".kills")+" | Ölüm "+data.getInt(key(p)+".deaths")+" | XP "+data.getLong(key(p)+".xp")+" | Seviye "+data.getInt(key(p)+".level",1)); return true; }
        if(cmd.equals("daily")) { if(playerOnly(sender))return true; Player p=(Player)sender; long today=System.currentTimeMillis()/86400000L; if(data.getLong(key(p)+".daily",-1)==today){msg(p,"&cGünlük ödülünü zaten aldın.");return true;} data.set(key(p)+".daily",today);balance(p,balance(p)+100);msg(p,"&aGünlük ödülün: 100 coin!");return true; }
        if(cmd.equals("quest")) { if(playerOnly(sender))return true; Player p=(Player)sender; msg(p,"&eGünlük görev: 10 blok kır. İlerleme: "+data.getInt(key(p)+".quest.blocks")+"/10");return true; }
        if(cmd.equals("xp")) { if(playerOnly(sender))return true; Player p=(Player)sender; long xp=data.getLong(key(p)+".xp"); if(a.length>0&&admin(sender)){try{xp+=Long.parseLong(a[0]);data.set(key(p)+".xp",Math.max(0,xp));save();}catch(NumberFormatException ignored){}} msg(p,"&eXP: "+data.getLong(key(p)+".xp")+" | Seviye: "+data.getInt(key(p)+".level",1));return true; }
        if(cmd.equals("tpa")) { if(playerOnly(sender))return true; if(a.length<1){msg(sender,"&c/tpa <oyuncu>");return true;} Player p=(Player)sender,t=online(a[0]);if(t==null||t.equals(p)){msg(p,"&cOyuncu bulunamadı.");return true;}tpa.put(t.getUniqueId(),p.getUniqueId());tpaCreated.put(t.getUniqueId(),System.currentTimeMillis());msg(t,"&e"+p.getName()+" sana ışınlanmak istiyor. /tpaccept veya /tpdeny");msg(p,"&aIşınlanma isteği gönderildi.");return true; }
        if(cmd.equals("tpaccept")||cmd.equals("tpdeny")) { if(playerOnly(sender))return true; Player p=(Player)sender;UUID id=tpa.remove(p.getUniqueId());tpaCreated.remove(p.getUniqueId());if(id==null){msg(p,"&cBekleyen istek yok.");return true;}Player from=Bukkit.getPlayer(id);if(from==null){msg(p,"&cOyuncu artık çevrimiçi değil.");return true;}if(cmd.equals("tpaccept")){lastLocations.put(from.getUniqueId(),from.getLocation());from.teleport(p.getLocation());msg(from,"&aIşınlandın.");msg(p,"&aİstek kabul edildi.");}else{msg(from,"&cIşınlanma isteğin reddedildi.");}return true; }
        if(cmd.equals("sethome")||cmd.equals("home")||cmd.equals("delhome")) { if(playerOnly(sender))return true;Player p=(Player)sender;String name=a.length>0?a[0].toLowerCase(Locale.ROOT):"default";String path=key(p)+".homes."+name;if(cmd.equals("sethome")){writeLocation(path,p.getLocation());msg(p,"&aEv kaydedildi: "+name);}else if(cmd.equals("delhome")){data.set(path,null);save();msg(p,"&aEv silindi.");}else{Location l=readLocation(path);if(l==null)msg(p,"&cEv bulunamadı.");else{lastLocations.put(p.getUniqueId(),p.getLocation());p.teleport(l);msg(p,"&aEvine ışınlandın.");}}return true; }
        if(cmd.equals("setwarp")||cmd.equals("warp")||cmd.equals("delwarp")) { if(cmd.equals("setwarp")||cmd.equals("delwarp")){if(!admin(sender))return true;}if(cmd.equals("warp")&&playerOnly(sender))return true;if(a.length<1){msg(sender,"&c/"+cmd+" <isim>");return true;}String path="warps."+a[0].toLowerCase(Locale.ROOT);if(cmd.equals("setwarp")){if(!(sender instanceof Player p)){msg(sender,"&cOyuncu olmalısın.");return true;}writeLocation(path,p.getLocation());msg(p,"&aWarp kaydedildi.");}else if(cmd.equals("delwarp")){data.set(path,null);save();msg(sender,"&aWarp silindi.");}else{Location l=readLocation(path);if(l==null)msg(sender,"&cWarp bulunamadı.");else{Player p=(Player)sender;lastLocations.put(p.getUniqueId(),p.getLocation());p.teleport(l);msg(p,"&aWarp'a ışınlandın.");}}return true; }
        if(cmd.equals("setjail") || cmd.equals("setarena")){if(!admin(sender))return true;if(!(sender instanceof Player p)){msg(sender,"&cBu komut oyuncu tarafından kullanılmalı.");return true;}String path=cmd.equals("setjail")?"jail":"arena";if(cmd.equals("setarena")){if(a.length<1 || (!a[0].equals("1")&&!a[0].equals("2"))){msg(p,"&c/setarena <1|2>");return true;}path="arena."+a[0];}writeLocation(path,p.getLocation());msg(p,"&aKonum kaydedildi: "+path);return true;}
        if(cmd.equals("setspawn")){if(!admin(sender))return true;if(!(sender instanceof Player p)){msg(sender,"&cOyuncu olmalısın.");return true;}writeLocation("spawn",p.getLocation());msg(p,"&aSpawn kaydedildi.");return true;}
        if(cmd.equals("spawn")){if(playerOnly(sender))return true;Player p=(Player)sender;Location l=readLocation("spawn");if(l==null)msg(p,"&cSpawn ayarlanmamış.");else{lastLocations.put(p.getUniqueId(),p.getLocation());p.teleport(l);msg(p,"&aSpawn'a ışınlandın.");}return true;}
        if(cmd.equals("back")){if(playerOnly(sender))return true;Player p=(Player)sender;Location l=lastLocations.get(p.getUniqueId());if(l==null)msg(p,"&cDönülecek konum yok.");else p.teleport(l);return true;}
        if(cmd.equals("kick")||cmd.equals("ban")||cmd.equals("mute")||cmd.equals("unmute")||cmd.equals("warn")||cmd.equals("jail")||cmd.equals("unjail")){if(!admin(sender))return true;if(a.length<1){msg(sender,"&c/"+cmd+" <oyuncu> [sebep]");return true;}OfflinePlayer off=Bukkit.getOfflinePlayer(a[0]);Player target=online(a[0]);String reason=a.length>1?String.join(" ",Arrays.copyOfRange(a,1,a.length)):"Yetkili işlemi";if(cmd.equals("kick")){if(target!=null)target.kickPlayer(reason);}else if(cmd.equals("ban")){Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(a[0],reason,null,sender.getName());if(target!=null)target.kickPlayer(reason);}else if(cmd.equals("mute")){muted.add(off.getUniqueId());save();}else if(cmd.equals("unmute")){muted.remove(off.getUniqueId());save();}else if(cmd.equals("jail")){Location jail=readLocation("jail");if(jail==null){msg(sender,"&cÖnce /setjail ile hapishane konumunu ayarla.");return true;}jailed.add(off.getUniqueId());save();if(target!=null)target.teleport(jail);}else if(cmd.equals("unjail")){jailed.remove(off.getUniqueId());save();}else{List<String>w=data.getStringList("warnings."+off.getUniqueId());w.add(reason);data.set("warnings."+off.getUniqueId(),w);save();}msg(sender,"&aİşlem uygulandı.");return true;}
        if(cmd.equals("crate")||cmd.equals("key")){if(playerOnly(sender))return true;Player p=(Player)sender;if(cmd.equals("key")){if(!admin(sender))return true;if(a.length<1){msg(p,"&c/key <oyuncu> [adet]");return true;}Player t=online(a[0]);if(t==null)return true;int n=1;try{if(a.length>1)n=Integer.parseInt(a[1]);}catch(NumberFormatException ignored){}data.set(key(t)+".keys",data.getInt(key(t)+".keys")+Math.max(1,n));save();msg(t,"&aKasa anahtarı aldın.");}else{int keys=data.getInt(key(p)+".keys");if(keys<1){msg(p,"&cAnahtarın yok.");return true;}data.set(key(p)+".keys",keys-1);balance(p,balance(p)+new Random().nextInt(251));msg(p,"&aKasa açıldı! Ödül bakiyene eklendi.");}return true;}
        if(cmd.equals("coinflip")){if(playerOnly(sender))return true;if(a.length<1){msg(sender,"&c/coinflip <miktar>");return true;}Player p=(Player)sender;double bet;try{bet=Double.parseDouble(a[0]);}catch(NumberFormatException ex){return true;}if(bet<=0||balance(p)<bet){msg(p,"&cGeçersiz bahis.");return true;}balance(p,balance(p)-bet);if(new Random().nextBoolean()){balance(p,balance(p)+bet*2);msg(p,"&aKazandın!");}else msg(p,"&cKaybettin.");return true;}
        if(cmd.equals("duel")||cmd.equals("duelaccept")){if(playerOnly(sender))return true;Player p=(Player)sender;if(cmd.equals("duel")){if(a.length<1)return true;Player t=online(a[0]);if(t==null||t.equals(p))return true;duels.put(t.getUniqueId(),p.getUniqueId());msg(t,"&e"+p.getName()+" düello istiyor. /duelaccept");msg(p,"&aDüello isteği gönderildi.");}else{UUID id=duels.remove(p.getUniqueId());Player t=id==null?null:Bukkit.getPlayer(id);if(t==null){msg(p,"&cBekleyen düello yok.");return true;}Location l1=readLocation("arena.1"),l2=readLocation("arena.2");if(l1==null||l2==null){msg(p,"&cArena hazır değil. Yetkili /setarena 1 ve /setarena 2 ayarlamalı.");return true;}lastLocations.put(p.getUniqueId(),p.getLocation());lastLocations.put(t.getUniqueId(),t.getLocation());p.teleport(l1);t.teleport(l2);msg(p,"&aDüello başladı!");msg(t,"&aDüello başladı!");}return true;}
        if(cmd.equals("report")){if(playerOnly(sender))return true;if(a.length<2){msg(sender,"&c/report <oyuncu> <sebep>");return true;}List<String> reports=data.getStringList("reports");reports.add(sender.getName()+" -> "+a[0]+": "+String.join(" ",Arrays.copyOfRange(a,1,a.length)));data.set("reports",reports);save();msg(sender,"&aRapor gönderildi.");return true;}
        if(cmd.equals("maintenance")){if(!admin(sender))return true;maintenance=!maintenance;save();msg(sender,"&eBakım modu: "+(maintenance?"Açık":"Kapalı"));return true;}
        if(cmd.equals("announce")){if(!admin(sender))return true;if(a.length==0)return true;Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',"&6[DUYURU] &f"+String.join(" ",a)));return true;}
        return false;
    }

    private void openMarket(Player p) {
        Inventory inv=Bukkit.createInventory(null,54,ChatColor.GREEN+"Oyuncu Pazarı");
        var listings=data.getConfigurationSection("market.listings");
        int displaySlot=0;
        if(listings!=null) for(String id:listings.getKeys(false)) {
            ItemStack stored=data.getItemStack("market.listings."+id+".item");
            if(stored==null||stored.getType()==Material.AIR) continue;
            ItemStack display=stored.clone(); ItemMeta meta=display.getItemMeta();
            List<String> lore=meta.hasLore()?new ArrayList<>(meta.getLore()):new ArrayList<>();
            lore.add(ChatColor.DARK_GRAY+"İlan ID: "+id);
            lore.add(ChatColor.GRAY+"Satıcı: "+data.getString("market.listings."+id+".seller-name","Bilinmiyor"));
            lore.add(ChatColor.GOLD+"Fiyat: "+String.format(Locale.US,"%.2f",data.getDouble("market.listings."+id+".price"))+" coin");
            lore.add(ChatColor.GREEN+"Satın almak için tıkla");
            meta.setLore(lore); display.setItemMeta(meta);
            if(displaySlot<54) inv.setItem(displaySlot++,display);
        }
        p.openInventory(inv);
    }
    private void purchaseListing(Player buyer,int id) {
        String path="market.listings."+id;
        String sellerText=data.getString(path+".seller");
        if(sellerText==null){msg(buyer,"&cBu ilan artık mevcut değil.");return;}
        UUID seller;try{seller=UUID.fromString(sellerText);}catch(IllegalArgumentException ex){msg(buyer,"&cİlan verisi hatalı.");return;}
        if(seller.equals(buyer.getUniqueId())){msg(buyer,"&cKendi ilanını satın alamazsın.");return;}
        double price=data.getDouble(path+".price");
        ItemStack item=data.getItemStack(path+".item");
        if(item==null||item.getType()==Material.AIR||!Double.isFinite(price)||price<=0){msg(buyer,"&cİlan geçersiz.");return;}
        if(balance(buyer)<price){msg(buyer,"&cBu ilanı almak için paran yetersiz.");return;}
        if(buyer.getInventory().firstEmpty()==-1){msg(buyer,"&cEnvanterinde boş yer yok.");return;}
        buyer.getInventory().addItem(item.clone());
        balance(buyer.getUniqueId(),balance(buyer)-price);
        balance(seller,balance(seller)+price);
        String sellerName=data.getString(path+".seller-name","Satıcı");
        data.set(path,null);save();
        msg(buyer,"&a"+sellerName+" adlı oyuncunun ilanını satın aldın.");
        Player onlineSeller=Bukkit.getPlayer(seller);if(onlineSeller!=null)msg(onlineSeller,"&a#"+id+" numaralı ilanının satıldı. +"+String.format(Locale.US,"%.2f",price)+" coin");
    }
    private void showBalanceTop(CommandSender sender) {
        var section=data.getConfigurationSection("players");
        if(section==null||section.getKeys(false).isEmpty()){msg(sender,"&7Henüz para sıralaması için kayıt yok.");return;}
        List<Map.Entry<String,Double>> entries=new ArrayList<>();
        for(String id:section.getKeys(false)) entries.add(Map.entry(id,data.getDouble("players."+id+".balance",0)));
        entries.sort((x,y)->Double.compare(y.getValue(),x.getValue()));
        msg(sender,"&6&lEn Zengin Oyuncular");
        int rank=0;for(var entry:entries){if(rank>=10)break;rank++;String name=data.getString("players."+entry.getKey()+".name","Bilinmeyen");msg(sender,"&e"+rank+". &f"+name+" &8» &a"+String.format(Locale.US,"%.2f",entry.getValue())+" coin");}
    }
    private void addItem(Inventory inv,Material type,String name,String price,int slot){ItemStack item=new ItemStack(type);ItemMeta meta=item.getItemMeta();meta.setDisplayName(ChatColor.YELLOW+name);meta.setLore(List.of(ChatColor.GRAY+"Fiyat: "+price+" coin",ChatColor.GREEN+"Satın almak için tıkla"));item.setItemMeta(meta);inv.setItem(slot,item);}
    @EventHandler public void onInventory(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p)||!e.getView().getTitle().equals(ChatColor.GREEN+"Oyuncu Pazarı"))return;
        e.setCancelled(true);if(e.getRawSlot()<0||e.getRawSlot()>=e.getView().getTopInventory().getSize())return;
        ItemStack item=e.getCurrentItem();if(item==null||!item.hasItemMeta()||!item.getItemMeta().hasLore())return;
        for(String line:item.getItemMeta().getLore()) if(ChatColor.stripColor(line).startsWith("İlan ID:")) {
            try{int id=Integer.parseInt(ChatColor.stripColor(line).substring("İlan ID:".length()).trim());purchaseListing(p,id);p.closeInventory();}catch(NumberFormatException ignored){} return;
        }
    }
    @EventHandler public void onMarketDrag(InventoryDragEvent e){if(e.getView().getTitle().equals(ChatColor.GREEN+"Oyuncu Pazarı"))e.setCancelled(true);}
    @EventHandler public void onJoin(PlayerJoinEvent e){Player p=e.getPlayer();playStarts.put(p.getUniqueId(),System.currentTimeMillis());if(maintenance&&!p.hasPermission("sulocore.admin")){p.kickPlayer("Sunucu bakım modunda.");return;}data.set(key(p)+".name",p.getName());if(!p.hasPlayedBefore())Bukkit.broadcastMessage(ChatColor.GREEN+"Aramıza hoş geldin, "+p.getName()+"!");}
    @EventHandler public void onQuit(PlayerQuitEvent e){UUID id=e.getPlayer().getUniqueId();Long start=playStarts.remove(id);if(start!=null)data.set(key(e.getPlayer())+".playtime",data.getLong(key(e.getPlayer())+".playtime")+(System.currentTimeMillis()-start)/1000);save();}
    @EventHandler public void onChat(AsyncPlayerChatEvent e){Player p=e.getPlayer();if(muted.contains(p.getUniqueId())||jailed.contains(p.getUniqueId())){e.setCancelled(true);return;}long now=System.currentTimeMillis();Long last=chatTimes.put(p.getUniqueId(),now);if(last!=null&&now-last<1000){e.setCancelled(true);p.sendMessage(ChatColor.RED+"Çok hızlı mesaj gönderiyorsun.");return;}String text=e.getMessage().toLowerCase(Locale.ROOT);for(String word:plugin.getConfig().getStringList("chat-filter.words"))if(!word.isBlank()&&text.contains(word.toLowerCase(Locale.ROOT))){e.setCancelled(true);p.sendMessage(ChatColor.RED+"Mesajın sohbet filtresine takıldı.");return;}}
    @EventHandler public void onDeath(PlayerDeathEvent e){Player p=e.getEntity();data.set(key(p)+".deaths",data.getInt(key(p)+".deaths")+1);if(p.getKiller()!=null){Player k=p.getKiller();data.set(key(k)+".kills",data.getInt(key(k)+".kills")+1);data.set(key(k)+".xp",data.getLong(key(k)+".xp")+10);}}
    @EventHandler public void onBlockBreak(org.bukkit.event.block.BlockBreakEvent e){Player p=e.getPlayer();String k=key(p)+".quest.blocks";data.set(k,data.getInt(k)+1);if(data.getInt(k)==10){balance(p,balance(p)+200);msg(p,"&aGörev tamamlandı! +200 coin");data.set(k,0);}}
    private void autoMessages(){List<String> messages=plugin.getConfig().getStringList("automation.messages");if(messages.isEmpty())return;String m=messages.get(new Random().nextInt(messages.size()));Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',"&8[&aBilgi&8] &f"+m));}
}
