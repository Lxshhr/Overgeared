package net.stirdrem.overgeared.compat.emi;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;

/**
 * Abstract base class for alloy smelting EMI recipes.
 * Handles common layout logic for both 2x2 and 3x3 input grids.
 *
 * @param <T> The recipe type (AlloySmeltingRecipe or NetherAlloySmeltingRecipe)
 */
public abstract class AbstractAlloySmeltingEmiRecipe<T extends Recipe<?>> implements EmiRecipe {
    
    protected static final int SLOT_SIZE = 18;
    protected static final int ARROW_WIDTH = 24;
    protected static final int OFFSET_X = 12;
    protected static final int OFFSET_Y = 6;
    
    protected final ResourceLocation id;
    protected final T recipe;
    protected final List<EmiIngredient> inputs;
    protected final List<EmiStack> outputs;

    protected AbstractAlloySmeltingEmiRecipe(RecipeHolder<T> holder, 
                                              List<Ingredient> ingredients, 
                                              EmiStack output) {
        this.id = holder.id();
        this.recipe = holder.value();
        this.inputs = ingredients.stream()
                .map(EmiIngredient::of)
                .toList();
        this.outputs = List.of(output);
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

    /**
     * @return The number of columns in the input grid (2 or 3)
     */
    protected abstract int getGridColumns();

    /**
     * @return The cooking time in ticks
     */
    protected abstract int getCookingTime();

    /**
     * @return The experience gained from the recipe
     */
    protected abstract float getExperience();

    @Override
    public int getDisplayWidth() {
        int gridWidth = getGridColumns() * SLOT_SIZE;
        // Grid + gap + arrow + gap + large slot + padding
        return OFFSET_X * 2 + gridWidth + 6 + ARROW_WIDTH + 4 + 26;
    }

    @Override
    public int getDisplayHeight() {
        int gridHeight = getGridColumns() * SLOT_SIZE;
        return Math.max(gridHeight + OFFSET_Y * 2, 50);
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        int gridX = OFFSET_X;
        int gridY = OFFSET_Y;
        int gridCols = getGridColumns();
        int gridSize = gridCols * SLOT_SIZE;
        
        // Input grid
        int inputCount = inputs.size();
        for (int row = 0; row < gridCols; row++) {
            for (int col = 0; col < gridCols; col++) {
                int index = row * gridCols + col;
                int x = gridX + col * SLOT_SIZE;
                int y = gridY + row * SLOT_SIZE;
                
                if (index < inputCount) {
                    widgets.addSlot(inputs.get(index), x, y);
                } else {
                    widgets.addSlot(EmiStack.EMPTY, x, y);
                }
            }
        }
        
        // Arrow (centered vertically with grid)
        int arrowX = gridX + gridSize + 6;
        int arrowY = gridY + (gridSize - 17) / 2 - 8; // 17 = arrow height, offset up a bit
        widgets.addTexture(EmiTexture.EMPTY_ARROW, arrowX, arrowY);
        widgets.addFillingArrow(arrowX, arrowY, getCookingTime() * 50);
        
        // Fire directly under arrow
        int fireX = arrowX + 5;
        int fireY = arrowY + 18;
        widgets.addTexture(EmiTexture.EMPTY_FLAME, fireX, fireY);
        widgets.addAnimatedTexture(EmiTexture.FULL_FLAME, fireX, fireY, 4000, false, true, true);
        
        // Output (large slot)
        int outputX = arrowX + ARROW_WIDTH + 4;
        int outputY = gridY + (gridSize - 26) / 2 - 4; // 26 = large slot height
        widgets.addSlot(outputs.getFirst(), outputX, outputY).large(true).recipeContext(this);
        
        // XP text under output
        float xp = getExperience();
        if (xp > 0) {
            widgets.addText(Component.translatable("emi.cooking.experience", xp), outputX, outputY + 28, 0xFF808080, false);
        }
    }
}
