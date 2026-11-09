package com.muxigame.buildingarchive;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import java.util.*;

/** Short steps first; technical details are an explicit second level. Text paginates at the actual GUI size. */
public final class ArchiveHelpScreen extends Screen {
 private final Screen parent;private boolean advanced;private int page;private List<List<FormattedCharSequence>> pages=List.of();
 private static final String BASIC="""
先记住这一句
框住建筑 → 点保存 → 选位置 → 点放置。
建筑档案仪负责干活，这本书负责教你怎么用。

第一步：把整栋建筑框住
拿出「建筑档案仪」。右键建筑的一角，再右键斜对面的另一角。不用按潜行。
第二次点击后会出现面板。两个角就像一个大盒子的两端，屋顶、墙和地板都要装进盒子里。
屋顶没选进去？在面板填「最低 Y / 最高 Y」，点「应用上下高度」，再回现场看看边框。Y 越大，位置越高。

第二步：给建筑存一份
在「建筑名称」里写名字，点「保存选区」。可以用中文；不写也会自动起名。
保存只做一份拷贝，不会搬走原建筑。同名不会覆盖，换个名字就好。
下次要用时，右键空气打开面板，在右侧列表点建筑名字。建筑多了就点下一页。

第三步：把建筑放出来
载入建筑后，点「返回现场」。瞄准64格内的地面右键，就能远程定位。
黄色框是范围，绿色是底面，蓝色是简化外形。位置会固定，不会跟着视角乱跑。
用侧边面板的东、西、南、北、上升、下降调整，每次1格或5格。最远可调整到离你96格；区域需已加载。
点「返回现场」可以绕行检查，再右键空气回到调整面板。想换地方就取消投影后重新瞄准。
方向不对？点「左转90° / 右转90°」，投影和占地一起旋转；原点保持不动。
需要左右翻转？点「左右镜像」，会沿东西方向对调，楼层高低不变；再点一次还原。镜像只影响这次投影，原档案不变。可以配合旋转调整方向。
满意就点「确认放置建筑」。这时才会真正放下建筑。
位置不对？点「取消目标」，回现场换个位置再试。
目标已有东西时会出现「覆盖放置」。覆盖可能清掉原来的东西，请读完提示再点第二次。

给已记录的建筑改名
在档案列表点击名字旁的「改名」，直接输入新名称，再点「保存」。点「取消」保留原名。支持中文，最多64字，不能与其他记录重名。
建筑和统计文件一起改名，已载入的建筑保留旋转和投影位置，世界里的建筑不变。

删除已记录的建筑
在右侧档案列表找到建筑，点击名字旁的「删除」。弹出的确认窗口会写出完整建筑名，确认后从列表移除。
这只删除保存的记录，世界里的建筑、箱子和NPC都不会改变。正在载入这份记录的玩家也会停止预览。
记录及统计文件移入世界存档/building_archive/deleted/下的独立备份文件夹。需要恢复时将其中NBT和统计文件放回building_archive顶层，再刷新列表。

放错了，怎么办？
打开面板，点「撤销上次操作」，读提示后再点一次。只能撤销本次登录最近的一次放置或删除。
要存下一栋，先点「重新选区」，回现场重新选两角。

找不到面板或保存按钮不能点？
拿着档案仪右键空气，就能打开面板。
先选齐两个角，范围不要太大。如果已经载入了建筑，点「重新选区」再开始。
工具目前只给创造模式玩家或管理员使用。查看这本指南不受限制。

地形改造工具
同一创造分类里还有「地形改造工具」。它用于批量替换、清理植物/薄雪和填补空气。右键选两角，再右键空气打开面板；中文方块列表支持搜索。先预览数量再确认，详细说明在工具面板的「操作说明」中。

建筑文件怎么交给朋友，怎么上传到服务器？
取出「建筑导入导出器」右键打开，或输入 /ba files。
上传：选择下载目录/桌面里的NBT或建筑作品ZIP，再点上传。也可粘贴完整文件路径。联机需二级管理员；单人创造可用。
下载：先用档案仪保存选区，再切到「下载已记录建筑」，点建筑名称即可保存到本机游戏目录的「建筑导出」。复制导出目录按钮便于查找。
把NBT发给朋友即可；旁边JSON是自动生成的方块统计。同名上传会拒绝，不会覆盖；ZIP批量导入按原名，遇到错误停止并保留此前成功的建筑。
每份文件最多8 MB，ZIP最多64份/32 MB，只支持同版NBT，不支持schem/litematic。已有JSON的手写风格注释不随传输保留，请另存。

工具在哪里？
创造物品栏的「建筑档案」分类里有档案仪和本指南。
已有档案仪也可从面板右上角的「使用帮助」进入这里。
想了解文件、方块统计和更详细的限制，再点上方「进阶说明」。
""";
 private static final String ADVANCED="""
选区与放置方向
两角所在的方块都算在选区里。每边最多128格，整个盒子最多32768格，空气也占格数。
区域需要已加载，且在世界边界和建筑高度以内。超限会拒绝，不会偷偷截掉建筑。
放置原点是原选区坐标最小的一角，向世界 +X、+Y、+Z 延伸，不跟随玩家朝向。支持水平90°旋转；暂不支持镜像。旋转以最小角为固定原点，长宽互换；不会修改磁盘原档案。原版方块朝向随之旋转，模组方块的私有方块实体朝向字段需由其自身支持。
投影的黄色框是整体范围，绿色是底面，蓝色是按整格方块简化的建筑轮廓，并非真实材质。复杂外形超过2048条合并段时只显示整体范围。手持工具时持续显示；换维度或退出会清除。

到底保存了什么？
保存方块、朝向、半砖位置、含水状态，以及箱子等方块实体的数据。
不保存玩家、NPC、生物、掉落物、画和盔甲架；不保存计划刻或其他模组的外部数据库。
复杂机器不保证运行状态完全还原。放下后仍会受重力、流体和红石规则影响。

覆盖和撤销
建筑里的空气也会放入目标区域，因此覆盖会清空对应位置的原方块。
每次放置前，原区域会自动备份到磁盘。放错后可以撤销最近一次，但撤销也会覆盖那片区域后来发生的编辑。
退出后会清除选区、载入状态和会话撤销。换维度后，要回原维度才能撤销。磁盘档案和备份仍保留。
缺失模组方块或无效状态会拒绝导入，不会直接变成空气。

文件在哪里？
文件在「世界存档/building_archive/」里。联机时在服务器电脑，不会自动下载到你的电脑。
名字.nbt 是建筑本体，重新导入靠它。名字.analysis.json 是方块统计，方便发给AI讨论建筑风格。
文件名可用中文、字母、数字、下划线和短横线，最多64字；同名不覆盖。
跨世界使用时，把 NBT 复制到另一个世界的同名文件夹，再从档案列表载入。两边需使用兼容的游戏版本和相关方块模组。
暂不直接读取 schem、schematic 或 litematic 文件。

怎么分析建筑风格？
载入后，鼠标停在面板的方块统计上，可查看数量最多的五种方块。
完整 JSON 包含尺寸、原坐标、维度、每种方块数量、模组来源、各高度层分布和完整状态。
这里统计的是实际方块格数，不是合成所需原料。
styleTags 与 styleNotes 留给人填写。模组不会根据几种方块就断言建筑是什么风格。
讨论风格时，提供统计 JSON 和建筑截图；要重新放回建筑，还要保留 NBT。

旧命令仍能用
/ba 打开面板；/ba give 获取档案仪。
/ba export 名称 保存；/ba list 列表；/ba load 名称 载入。
/ba preview 用脚下坐标预览；/ba paste 放置；/ba paste replace 明确覆盖。
/ba undo 撤销；/ba select 重新选区；/ba pos1 和 pos2 用脚下坐标选角。
旧的潜行右键仍可选第二角。潜行右键空气现在打开面板，不会立刻保存。

恢复更早的备份（进阶）
backups/ 中保存了放置前的原区域 NBT。先备份当前世界，再将需要的备份复制到 building_archive 顶层。
通过档案面板载入，在当时正确的维度和原点放置。备份不是自动定位还原，务必核对位置。
""";
 public ArchiveHelpScreen(Screen parent){super(Component.literal("建筑档案使用指南"));this.parent=parent;}
 private int panelWidth(){return Math.min(380,width-12);}private int panelHeight(){return Math.min(260,height-12);}private int left(){return (width-panelWidth())/2;}private int top(){return (height-panelHeight())/2;}
 @Override protected void init(){clearWidgets();int x=left(),y=top(),w=panelWidth(),h=panelHeight();int linesPerPage=Math.max(1,(h-100)/12);var lines=new ArrayList<FormattedCharSequence>();for(String line:(advanced?ADVANCED:BASIC).split("\n",-1)){if(line.isEmpty())lines.add(Component.literal(" ").getVisualOrderText());else lines.addAll(font.split(Component.literal(line),w-24));}
  pages=new ArrayList<>();for(int i=0;i<lines.size();i+=linesPerPage)pages.add(new ArrayList<>(lines.subList(i,Math.min(i+linesPerPage,lines.size()))));page=Math.min(page,pages.size()-1);
  addRenderableWidget(Button.builder(Component.literal(advanced?"轻松上手":"【轻松上手】"),b->{advanced=false;page=0;init();}).bounds(x+12,y+28,(w-28)/2,18).build());
  addRenderableWidget(Button.builder(Component.literal(advanced?"【进阶说明】":"进阶说明"),b->{advanced=true;page=0;init();}).bounds(x+16+(w-28)/2,y+28,(w-28)/2,18).build());
  var prev=addRenderableWidget(Button.builder(Component.literal("上一页"),b->{page--;init();}).bounds(x+12,y+h-26,72,18).build());prev.active=page>0;
  var next=addRenderableWidget(Button.builder(Component.literal("下一页"),b->{page++;init();}).bounds(x+88,y+h-26,72,18).build());next.active=page+1<pages.size();
  addRenderableWidget(Button.builder(Component.literal(parent==null?"读完了，返回游戏":"返回档案面板"),b->onClose()).bounds(x+w-152,y+h-26,140,18).build());
 }
 @Override public void render(GuiGraphics g,int mx,int my,float partial){int x=left(),y=top(),w=panelWidth(),h=panelHeight();g.fill(x,y,x+w,y+h,0xf0182735);g.drawString(font,title,x+12,y+10,0xffe4dbc0,false);int yy=y+54;for(var line:pages.get(page)){g.drawString(font,line,x+12,yy,0xffe0e8e9,false);yy+=12;}g.drawString(font,(advanced?"进阶说明":"轻松上手")+" · "+(page+1)+" / "+pages.size(),x+12,y+h-41,0xff8bd6cc,false);super.render(g,mx,my,partial);}
 @Override public void onClose(){minecraft.setScreen(parent);}
 @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial){}
 @Override public boolean isPauseScreen(){return false;}
}




