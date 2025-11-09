package com.aquavie.nodead;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.command.ConfigCommand;

import java.util.*;

@Mod(Main.MOD_ID)
public class Main {
    public static final String MOD_ID = "nodead";
    
    // 存储受保护的实体UUID
    private static final Set<UUID> PROTECTED_ENTITIES = new HashSet<>();
    
    // 存储每个受保护实体的血量
    private static final Map<UUID, Float> PROTECTED_HEALTH = new HashMap<>();
    
    public Main() {
        // 注册事件监听
        MinecraftForge.EVENT_BUS.register(this);
        
        // 注册命令
        registerCommands();
    }
    
    private void registerCommands() {
        // 在Forge的命令注册事件中处理
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.RegisterCommandsEvent event) -> {
            event.getDispatcher().register(Commands.literal("nodeath")
                .requires(source -> source.hasPermission(4))
                .then(Commands.literal("god")
                    .then(Commands.argument("entities", EntityArgument.entities())
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            Collection<? extends Entity> entities = EntityArgument.getEntities(context, "entities");
                            return enableGodMode(source, entities);
                        })))
                .then(Commands.literal("mortal")
                    .then(Commands.argument("entities", EntityArgument.entities())
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            Collection<? extends Entity> entities = EntityArgument.getEntities(context, "entities");
                            return disableGodMode(source, entities);
                        })))
                .then(Commands.literal("check")
                    .then(Commands.argument("entity", EntityArgument.entity())
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            Entity entity = EntityArgument.getEntity(context, "entity");
                            return checkProtectionStatus(source, entity);
                        })))
            );
            
            ConfigCommand.register(event.getDispatcher());
        });
    }
    
    private int enableGodMode(CommandSourceStack source, Collection<? extends Entity> entities) {
        int resultCount = 0;
        for (Entity entity : entities) {
            if (entity instanceof LivingEntity) {
                LivingEntity livingEntity = (LivingEntity) entity;
                PROTECTED_ENTITIES.add(entity.getUUID());
                // 存储当前血量
                PROTECTED_HEALTH.put(entity.getUUID(), livingEntity.getHealth());
                resultCount++;
            }
        }
        final int finalCount = resultCount;
        source.sendSuccess(() -> Component.literal("Enabled God Mode for " + finalCount + " entities"), true);
        return resultCount;
    }
    
    private int disableGodMode(CommandSourceStack source, Collection<? extends Entity> entities) {
        int resultCount = 0;
        for (Entity entity : entities) {
            if (PROTECTED_ENTITIES.remove(entity.getUUID())) {
                // 移除存储的血量
                PROTECTED_HEALTH.remove(entity.getUUID());
                resultCount++;
            }
        }
        final int finalCount = resultCount;
        source.sendSuccess(() -> Component.literal("Disabled God Mode for " + finalCount + " entities"), true);
        return resultCount;
    }
    
    private int checkProtectionStatus(CommandSourceStack source, Entity entity) {
        boolean hasTag = entity.getTags().contains("CannotDie");
        boolean isProtected = PROTECTED_ENTITIES.contains(entity.getUUID());
        boolean isCreativePlayer = entity instanceof Player && ((Player)entity).getAbilities().instabuild;
        
        String status = "Unprotected";
        if (hasTag || isProtected || isCreativePlayer) {
            status = "Protected";
        }
        
        final String entityName = entity.getDisplayName().getString();
        final String finalStatus = status;
        source.sendSuccess(() -> Component.literal(entityName + " Protection Status: " + finalStatus), true);
        return 1;
    }
    
    // Check if entity is protected
    public static boolean isEntityProtected(Entity entity) {
        // Tag protection
        if (entity.getTags().contains("CannotDie")) {
            return true;
        }
        
        // Command protection
        if (PROTECTED_ENTITIES.contains(entity.getUUID())) {
            return true;
        }
        
        // Creative players are automatically protected
        if (entity instanceof Player) {
            Player player = (Player) entity;
            if (player.getAbilities().instabuild) {
                return true;
            }
        }
        
        return false;
    }
    
    // Listen to death event, prevent protected entities from dying
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityDeath(LivingDeathEvent event) {
        Entity entity = event.getEntity();
        
        if (isEntityProtected(entity)) {
            // 取消死亡事件
            event.setCanceled(true);
            
            // Reset health
            LivingEntity livingEntity = (LivingEntity) entity;
            livingEntity.setHealth(livingEntity.getMaxHealth());
            
            // In Forge 1.20.1, no need to manually set death time
            
            // Notify player
            if (entity instanceof Player && !entity.level().isClientSide()) {
                ((Player) entity).displayClientMessage(Component.literal("You cannot die!"), true);
            }
        }
    }
    
    // Listen to entity join world event
    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        
        // Reset death-related flags
        if (entity instanceof LivingEntity) {
            LivingEntity livingEntity = (LivingEntity) entity;
            // In Forge 1.20.1, no need to manually set death time
            
            // Protect entity attributes
            if (isEntityProtected(entity)) {
                // Ensure max health is not zero
                AttributeInstance maxHealth = livingEntity.getAttribute(Attributes.MAX_HEALTH);
                if (maxHealth != null && maxHealth.getValue() <= 0) {
                    maxHealth.setBaseValue(20.0);
                }
                
                // Ensure movement speed is not zero
                AttributeInstance movementSpeed = livingEntity.getAttribute(Attributes.MOVEMENT_SPEED);
                if (movementSpeed != null && movementSpeed.getValue() <= 0) {
                    movementSpeed.setBaseValue(0.1);
                }
                
                // Ensure health is not zero
                if (livingEntity.getHealth() <= 0) {
                    livingEntity.setHealth(1.0F);
                }
            }
        }
    }
    
    // Listen to player respawn event (prevent loss of protection when respawning)
    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        Player player = event.getEntity();
        UUID originalUUID = original.getUUID();
        UUID newUUID = player.getUUID();
        
        if (PROTECTED_ENTITIES.contains(originalUUID)) {
            PROTECTED_ENTITIES.add(newUUID);
            // 复制存储的血量信息
            if (PROTECTED_HEALTH.containsKey(originalUUID)) {
                PROTECTED_HEALTH.put(newUUID, Math.min(PROTECTED_HEALTH.get(originalUUID), player.getMaxHealth()));
            }
        }
        
        // 确保克隆的玩家有CannotDie标签（如果原玩家有）
        if (original.getTags().contains("CannotDie")) {
            player.addTag("CannotDie");
            // 对于标签保护的玩家，存储其满血状态
            PROTECTED_HEALTH.put(newUUID, player.getMaxHealth());
        }
    }
    
    // Listen to player logout event
    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        // Maintain protection status, do not remove
    }
    
    // Listen to entity update event, periodically check and protect protected entities
    @SubscribeEvent
    public void onLivingUpdate(LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        
        if (isEntityProtected(entity)) {
            // Prevent health from being 0
            if (entity.getHealth() <= 0) {
                entity.setHealth(1.0F);
            }
            
            // Ensure entity attributes are normal
            AttributeInstance maxHealth = entity.getAttribute(Attributes.MAX_HEALTH);
            if (maxHealth != null && maxHealth.getValue() <= 0) {
                maxHealth.setBaseValue(20.0);
            }
            
            AttributeInstance movementSpeed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
            if (movementSpeed != null && movementSpeed.getValue() <= 0) {
                movementSpeed.setBaseValue(0.1);
            }
            
            // In Forge 1.20.1, no need to manually set death time
            
            // Try to reset removal flag via reflection
            try {
                java.lang.reflect.Field removedField = Entity.class.getDeclaredField("removed");
                removedField.setAccessible(true);
                removedField.set(entity, false);
            } catch (Exception e) {
                // 忽略错误
            }
        }
    }
    
    // Listen to damage event, restore health to stored value for protected entities
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLivingDamage(LivingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        UUID entityUUID = entity.getUUID();
        
        if (isEntityProtected(entity) && PROTECTED_HEALTH.containsKey(entityUUID)) {
            // 取消伤害事件
            event.setCanceled(true);
            
            // 恢复到存储的血量
            float storedHealth = PROTECTED_HEALTH.get(entityUUID);
            entity.setHealth(storedHealth);
            
            // Send prompt message (if player)
            if (entity instanceof Player) {
                Player player = (Player) entity;
                player.sendSystemMessage(Component.literal("[ God Mode ] You are protected, health restored!"));
            }
        }
    }
    
    // Listen to heal event, update stored health for protected entities
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLivingHeal(LivingHealEvent event) {
        LivingEntity entity = event.getEntity();
        UUID entityUUID = entity.getUUID();
        
        if (isEntityProtected(entity) && PROTECTED_HEALTH.containsKey(entityUUID)) {
            // 计算治疗后的血量
            float newHealth = Math.min(entity.getHealth() + event.getAmount(), entity.getMaxHealth());
            // 更新存储的血量
            PROTECTED_HEALTH.put(entityUUID, newHealth);
        }
    }
}