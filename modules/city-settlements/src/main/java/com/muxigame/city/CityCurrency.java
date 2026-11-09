package com.muxigame.city;

import java.util.*;
import net.minecraft.server.MinecraftServer;

/** Explicit opt-in issue/retirement ledger. Ordinary transfers and tax receipts never mint money. */
public final class CityCurrency {
 public static final class Policy {
  public boolean enabled,issuing,populationMint=true;public long clock,lastDay=-1,issued,retired,retireCents,minted;
  public Map<Long,Long> taxes=new TreeMap<>();public List<Day> days=new ArrayList<>();
 }
 public static final class Day {public long day,payroll,tax,before,issued,retired,after,minted;public int adults,population;}
 static boolean model(MinecraftServer s,UUID city){if(s==null)return false;var town=CityTrade.get(s).state.towns.get(city);return town!=null&&town.model;}
 static Policy policy(MinecraftServer s,UUID city){return CityTrade.get(s).state.currencies.get(city);}
 static void advance(MinecraftServer s,int ticks){for(var entry:CityTrade.get(s).state.currencies.entrySet())if(entry.getValue().enabled&&RuralSettlement.active(s,entry.getKey()))entry.getValue().clock+=ticks;}
 static void tax(MinecraftServer s,UUID city,long amount){var p=policy(s,city);if(p!=null&&p.enabled&&amount>0)p.taxes.merge(p.clock/24000,amount,Long::sum);}
 static long issued(MinecraftServer s){return CityTrade.get(s).state.currencies.values().stream().mapToLong(p->p.issued).sum();}
 static long minted(MinecraftServer s){return CityTrade.get(s).state.currencies.values().stream().mapToLong(p->p.minted).sum();}
 static long retired(MinecraftServer s){return CityTrade.get(s).state.currencies.values().stream().mapToLong(p->p.retired).sum();}
 static int adults(MinecraftServer s,UUID city){var d=RuralSettlement.get(s);return (int)d.state.people.values().stream().filter(p->!p.dead&&p.age.ordinal()>=2&&p.role!=RuralRules.Role.VAGRANT&&d.state.families.containsKey(p.family)&&city.equals(d.state.families.get(p.family).city)).count();}
 static long payroll(MinecraftServer s,UUID city){var c=CityData.get(s).require(city);long wages=0;for(var entry:CityMaintenance.get(s).state.workers.entrySet()){var w=entry.getValue();var person=RuralSettlement.get(s).state.people.get(entry.getKey());if(!w.enabled||!w.artisan||!w.city.equals(city)||!RuralSuccession.capable(person))continue;var cfg=WorkHours.config(s,entry.getKey());int segments=0;for(int t=1500;t<12000;t+=3000){double hour=(t/1000.0+6)%24;if(WorkHours.inWindow(hour,cfg.start,cfg.end))segments++;}wages+=(long)segments*CityWorksFinance.wage(s,c,true);}return wages;}
 static void settle(MinecraftServer s){var t=CityTrade.get(s);for(var entry:t.state.currencies.entrySet()){var p=entry.getValue();if(!p.enabled||!RuralSettlement.active(s,entry.getKey()))continue;long completed=p.clock/24000-1;if(completed<=p.lastDay)continue;
   // Normally one closed day; bounded catch-up if a diagnostic caller skipped ticks.
   for(int n=0;n<2&&p.lastDay<completed;n++){long day=++p.lastDay;settleDay(s,entry.getKey(),p,day);}t.setDirty();}}
 static Day settleDay(MinecraftServer s,UUID city,Policy p,long day){var e=CityEconomy.get(s);String account=CityEconomy.treasury(city);var row=new Day();row.day=day;row.payroll=payroll(s,city);row.adults=adults(s,city);row.tax=p.taxes.getOrDefault(day,0L);p.taxes.remove(day);row.before=e.balance(account);
  if(row.payroll>0&&row.adults>0){if(row.before<5*row.payroll)p.issuing=true;if(row.before>=7*row.payroll)p.issuing=false;
   if(p.issuing){row.issued=Math.min(row.adults*2L,Math.max(0,7*row.payroll-row.before));CityEconomy.require(e.balance(account)<=CityEconomy.LIMIT-row.issued,"发行超过账户上限");if(row.issued>0){e.state.accounts.put(account,e.balance(account)+row.issued);p.issued+=row.issued;e.log(RuralSettlement.get(s).state.simulationTicks,"CURRENCY_ISSUE","explicit_issuer",account,row.issued,"每日发行上限与财政阈值");}if(e.balance(account)>=7*row.payroll)p.issuing=false;}
   else if(row.issued==0&&e.balance(account)>14*row.payroll){long cents=Math.min(row.tax*10,Math.min(row.adults*10L,(e.balance(account)-14*row.payroll)*100));p.retireCents+=cents;row.retired=Math.min(p.retireCents/100,e.balance(account)-14*row.payroll);if(row.retired>0){p.retireCents-=row.retired*100;e.state.accounts.put(account,e.balance(account)-row.retired);p.retired+=row.retired;e.log(RuralSettlement.get(s).state.simulationTicks,"CURRENCY_RETIRE",account,"explicit_retirement",row.retired,"真实税收回笼注销，私人余额不老化");}}
  }else p.issuing=false;
  if(p.populationMint&&CityTrade.urban(s,city)){row.population=CityTrade.population(s,city);row.minted=Math.min(row.population,CityEconomy.LIMIT-e.balance(account));if(row.minted>0){e.state.accounts.put(account,e.balance(account)+row.minted);p.minted+=row.minted;p.issued+=row.minted;row.issued+=row.minted;e.log(RuralSettlement.get(s).state.simulationTicks,"CURRENCY_POPULATION_MINT","population_mint",account,row.minted,"城市每日每名存活人口一银币；婴儿除外");}}
  row.after=e.balance(account);p.days.add(row);if(p.days.size()>128)p.days.removeFirst();e.setDirty();return row;
 }
}
