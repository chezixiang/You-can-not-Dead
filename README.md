# You can't dead!

这是一个基于Minecraft Forge的模组，提供强大的实体死亡保护功能！

## 功能特点

### 自动保护
- **创造模式玩家自动获得保护**：所有处于创造模式的玩家默认受到死亡保护

### 命令控制（需要4级命令权限）
- `/nodeath god <entities>` - 为指定实体启用上帝模式（死亡保护）
- `/nodeath mortal <entities>` - 为指定实体禁用上帝模式
- `/nodeath check <entity>` - 检查指定实体的保护状态

### 标签保护
- 为任何实体添加 `CannotDie` 标签可使其获得永久保护

### 高级保护机制
本模组采用多种方式防止实体死亡，包括但不限于：
- 阻止LivingDeathEvent事件
- 通过Mixin保护实体的生命值相关方法
- 防止各种死亡状态篡改（isDead, deathTime等）
- 保护实体的属性不被恶意修改

## 技术信息

- Minecraft 版本：1.20.1
- Forge 版本：47.4.6
- 模组版本：1.0
- 作者：Aquavie
- 使用Mixin技术实现深度保护

## 构建说明

使用以下命令构建模组：

```
./gradlew build
```

构建完成后，模组文件将位于 `build/libs/` 目录下。

## 使用方法

1. 确保已安装 Minecraft Forge 1.20.1-47.4.6
2. 将构建好的模组文件放入 `.minecraft/mods/` 目录
3. 启动游戏即可使用保护功能

### 命令示例

```
# 为自己启用上帝模式
/nodeath god @s

# 为所有玩家禁用上帝模式
/nodeath mortal @a

# 检查某个生物的保护状态
/nodeath check @e[type=creeper,limit=1]

# 使用标签为实体添加永久保护
/tag @e[type=zombie] add CannotDie
```