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
import net.stirdrem.overgeared.recipe.AlloySmeltingRecipe;

import java.util.List;

public class AlloySmeltingEmiRecipe implements EmiRecipe {
    
    private final ResourceLocation id;
    private final AlloySmeltingRecipe recipe;
    private final List<EmiIngredient> inputs;
    private final List<EmiStack> outputs;

    public AlloySmeltingEmiRecipe(RecipeHolder<AlloySmeltingRecipe> holder) {
        this.id = holder.id();
        this.recipe = holder.value();
        
        this.inputs = recipe.getIngredientsList().stream()
                .map(EmiIngredient::of)
                .toList();
        
        this.outputs = List.of(EmiStack.of(recipe.getResultItem()));
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return OvergearedEmiPlugin.ALLOY_SMELTING_CATEGORY;
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
        return 118;
    }

    @Override
    public int getDisplayHeight() {
        return 50;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        // Layout: [2x2 Grid] [Arrow] [Output]
        //                    [Fire]  [XP]
        // Center the content: total content width ~94, display 118, so padding = 12 per side
        int offsetX = 12;
        int offsetY = 6; // Vertical padding
        int gridX = offsetX;
        int gridY = offsetY;
        
        int inputCount = inputs.size();
        
        // 2x2 input grid (36x36)
        widgets.addSlot(inputCount > 0 ? inputs.get(0) : EmiStack.EMPTY, gridX, gridY);
        widgets.addSlot(inputCount > 1 ? inputs.get(1) : EmiStack.EMPTY, gridX + 18, gridY);
        widgets.addSlot(inputCount > 2 ? inputs.get(2) : EmiStack.EMPTY, gridX, gridY + 18);
        widgets.addSlot(inputCount > 3 ? inputs.get(3) : EmiStack.EMPTY, gridX + 18, gridY + 18);
        
        // Arrow (centered vertically with grid: grid is 36px tall, arrow is 17px, so start at y=9)
        int arrowX = gridX + 36 + 6;
        int arrowY = gridY + 1; // Move up to center with grid top half
        widgets.addTexture(EmiTexture.EMPTY_ARROW, arrowX, arrowY);
        widgets.addFillingArrow(arrowX, arrowY, recipe.getCookingTime() * 50);
        
        // Fire directly under arrow
        int fireX = arrowX + 5;
        int fireY = arrowY + 18;
        widgets.addTexture(EmiTexture.EMPTY_FLAME, fireX, fireY);
        widgets.addAnimatedTexture(EmiTexture.FULL_FLAME, fireX, fireY, 4000, false, true, true);
        
        // Output (large slot, vertically centered with grid)
        int outputX = arrowX + 28;
        int outputY = gridY - 1;
        widgets.addSlot(outputs.getFirst(), outputX, outputY).large(true).recipeContext(this);
        
        // XP text under output
        float xp = recipe.getExperience();
        if (xp > 0) {
            widgets.addText(Component.translatable("emi.cooking.experience", xp), outputX, outputY + 30, 0xFF808080, false);
        }
    }
}
