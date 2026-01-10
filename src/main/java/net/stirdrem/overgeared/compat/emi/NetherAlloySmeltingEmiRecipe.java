package net.stirdrem.overgeared.compat.emi;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.stirdrem.overgeared.recipe.NetherAlloySmeltingRecipe;

import java.util.List;

public class NetherAlloySmeltingEmiRecipe implements EmiRecipe {
    
    private final ResourceLocation id;
    private final NetherAlloySmeltingRecipe recipe;
    private final List<EmiIngredient> inputs;
    private final List<EmiStack> outputs;

    public NetherAlloySmeltingEmiRecipe(RecipeHolder<NetherAlloySmeltingRecipe> holder) {
        this.id = holder.id();
        this.recipe = holder.value();
        
        this.inputs = recipe.getIngredientsList().stream()
                .map(EmiIngredient::of)
                .toList();
        
        this.outputs = List.of(EmiStack.of(recipe.getResultItem(null)));
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return OvergearedEmiPlugin.NETHER_ALLOY_SMELTING_CATEGORY;
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
        return 136;
    }

    @Override
    public int getDisplayHeight() {
        return 68;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        // Layout: [3x3 Grid] [Arrow] [Output]
        //                    [Fire]  [XP]
        // Center the content: total content width ~112, display 136, so padding = 12 per side
        int offsetX = 12;
        int offsetY = 6; // Vertical padding
        int gridX = offsetX;
        int gridY = offsetY;
        
        int inputCount = inputs.size();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int index = row * 3 + col;
                int x = gridX + col * 18;
                int y = gridY + row * 18;
                
                if (index < inputCount) {
                    widgets.addSlot(inputs.get(index), x, y);
                } else {
                    widgets.addSlot(EmiStack.EMPTY, x, y);
                }
            }
        }
        
        // Arrow (centered vertically with grid middle row)
        int arrowX = gridX + 54 + 6;
        int arrowY = gridY + 10; // Move up to center with grid
        widgets.addTexture(EmiTexture.EMPTY_ARROW, arrowX, arrowY);
        widgets.addFillingArrow(arrowX, arrowY, recipe.getCookingTime() * 50);
        
        // Fire directly under arrow
        int fireX = arrowX + 5;
        int fireY = arrowY + 18;
        widgets.addTexture(EmiTexture.EMPTY_FLAME, fireX, fireY);
        widgets.addAnimatedTexture(EmiTexture.FULL_FLAME, fireX, fireY, 4000, false, true, true);
        
        // Output (large slot, centered with grid)
        int outputX = arrowX + 28;
        int outputY = gridY + 8;
        widgets.addSlot(outputs.getFirst(), outputX, outputY).large(true).recipeContext(this);
        
        // XP text under output
        float xp = recipe.getExperience();
        if (xp > 0) {
            widgets.addText(Component.translatable("emi.cooking.experience", xp), outputX, outputY + 30, 0xFF808080, false);
        }
    }
}
