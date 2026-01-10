package net.stirdrem.overgeared.compat.emi;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.stirdrem.overgeared.components.CastData;
import net.stirdrem.overgeared.components.ModComponents;
import net.stirdrem.overgeared.item.ModItems;
import net.stirdrem.overgeared.recipe.CastingRecipe;
import net.stirdrem.overgeared.util.ConfigHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * EMI recipe display for Casting recipes.
 * Layout: [Material] [Arrow] [Output]
 *         [Cast]     [Fire]  [XP]
 */
public class CastingEmiRecipe implements EmiRecipe {

    private static final int SLOT_SIZE = 18;

    private final ResourceLocation id;
    private final CastingRecipe recipe;
    private final List<EmiIngredient> inputs;
    private final List<EmiStack> outputs;
    private final EmiStack emiCastStack;
    
    // Store material requirements for display-time resolution
    private final Map<String, Integer> requiredMaterials;

    public CastingEmiRecipe(RecipeHolder<CastingRecipe> holder) {
        this.id = holder.id();
        this.recipe = holder.value();

        // Store required materials for display-time lookup
        this.requiredMaterials = recipe.getRequiredMaterials();

        int maxRequired = requiredMaterials.values().stream().mapToInt(Integer::intValue).sum();

        // Create cast with the tool type and max value set
        this.emiCastStack = createCast(maxRequired);

        // Build inputs list (cast only - materials resolved at display time)
        List<EmiIngredient> inputList = new ArrayList<>();
        inputList.add(EmiIngredient.of(List.of(emiCastStack)));
        this.inputs = inputList;

        this.outputs = List.of(EmiStack.of(recipe.getResultItem(null)));
    }

    /**
     * Creates a cast ItemStack with the tool type and max value set.
     */
    private EmiStack createCast(int maxValue) {
        CastData castData = new CastData(
                "", // quality
                recipe.getToolType(),
                Map.of(), // empty materials (unfilled)
                0, // current
                maxValue, // max - set to total required materials
                List.of(), // input
                ItemStack.EMPTY, // output
                false // heated
        );

        ItemStack cast = new ItemStack(ModItems.CLAY_TOOL_CAST.get());
        cast.set(ModComponents.CAST_DATA.get(), castData);
        return EmiStack.of(cast);
    }
    
    /**
     * Builds material stacks at display time using config-based material values.
     * This allows the values to be resolved after tags and config are loaded.
     */
    private List<EmiStack> buildMaterialStacks() {
        List<EmiStack> validStacks = new ArrayList<>();
        
        for (Map.Entry<String, Integer> entry : requiredMaterials.entrySet()) {
            String materialId = entry.getKey().toLowerCase();
            int amountNeeded = entry.getValue();
            
            // Try different tag patterns
            String[] tagPatterns = {
                "c:storage_blocks/" + materialId,
                "c:ingots/" + materialId,
                "c:nuggets/" + materialId
            };
            
            for (String tagPath : tagPatterns) {
                TagKey<Item> tag = TagKey.create(Registries.ITEM, ResourceLocation.parse(tagPath));
                Ingredient ingredient = Ingredient.of(tag);
                
                ItemStack[] items = ingredient.getItems();
                for (ItemStack stack : items) {
                    // Skip barriers (empty tags) and air
                    if (stack.is(Items.BARRIER) || stack.isEmpty()) {
                        continue;
                    }
                    
                    // Use ConfigHelper to get the actual material value for this item
                    int materialValue = ConfigHelper.getMaterialValue(stack);
                    
                    // If config doesn't have this item, use default based on tag type
                    if (materialValue <= 0) {
                        if (tagPath.contains("storage_blocks")) {
                            materialValue = 81;
                        } else if (tagPath.contains("ingots")) {
                            materialValue = 9;
                        } else if (tagPath.contains("nuggets")) {
                            materialValue = 1;
                        }
                    }
                    
                    if (materialValue > 0) {
                        int count = (int) Math.ceil((double) amountNeeded / materialValue);
                        ItemStack copy = stack.copy();
                        copy.setCount(count);
                        validStacks.add(EmiStack.of(copy));
                    }
                }
            }
        }
        
        return validStacks;
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return OvergearedEmiPlugin.CASTING_CATEGORY;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public List<EmiIngredient> getInputs() {
        return inputs;
    }

    @Override
    public List<EmiStack> getOutputs() {
        return outputs;
    }

    @Override
    public int getDisplayWidth() {
        return 120;
    }

    @Override
    public int getDisplayHeight() {
        return 50;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        // Layout matching JEI:
        // [Material] [Arrow] [Output]
        // [Cast]     [Fire]  [XP]

        int offsetX = 20;
        int offsetY = 6;

        // Material slot (top-left) - resolve materials at display time
        int materialX = offsetX;
        int materialY = offsetY;

        // Build material stacks at display time using config values
        List<EmiStack> validStacks = buildMaterialStacks();

        if (validStacks.isEmpty()) {
            widgets.addSlot(EmiStack.EMPTY, materialX, materialY);
        } else {
            widgets.addSlot(EmiIngredient.of(validStacks), materialX, materialY);
        }

        // Cast slot (bottom-left) - shows empty tool cast
        int castX = offsetX;
        int castY = offsetY + SLOT_SIZE + 2;
        widgets.addSlot(emiCastStack, castX, castY);

        // Arrow (middle, vertically centered)
        int arrowX = offsetX + SLOT_SIZE + 8;
        int arrowY = offsetY;
        widgets.addTexture(EmiTexture.EMPTY_ARROW, arrowX, arrowY);
        widgets.addFillingArrow(arrowX, arrowY, recipe.getCookingTime() * 50);

        // Fire (below arrow)
        int fireX = arrowX + 5;
        int fireY = arrowY + 18;
        widgets.addTexture(EmiTexture.EMPTY_FLAME, fireX, fireY);
        widgets.addAnimatedTexture(EmiTexture.FULL_FLAME, fireX, fireY, 4000, false, true, true);

        // Output (large slot, right side)
        int outputX = arrowX + 28;
        int outputY = offsetY;
        widgets.addSlot(outputs.getFirst(), outputX, outputY).large(true).recipeContext(this);

        // XP text (next to fire)
        float xp = recipe.getExperience();
        if (xp > 0) {
            widgets.addText(Component.literal(xp + " XP"), fireX + 22, fireY + 9, 0xFF808080, false);
        }
    }
}
