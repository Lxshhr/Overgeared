package net.stirdrem.overgeared.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.stirdrem.overgeared.ForgingQuality;
import net.stirdrem.overgeared.components.ModComponents;
import net.stirdrem.overgeared.config.ServerConfig;

public class OvergearedShapelessRecipe extends ShapelessRecipe {

    private final NonNullList<IngredientWithRemainder> ingredientsWithRemainder;
    private final ItemStack result;

    public OvergearedShapelessRecipe(
            String group,
            CraftingBookCategory category,
            ItemStack result,
            NonNullList<Ingredient> ingredients,
            boolean[] remainder,
            int[] durability
    ) {
        super(group, category, result, ingredients);

        this.ingredientsWithRemainder = NonNullList.create();
        for (int i = 0; i < ingredients.size(); i++) {
            this.ingredientsWithRemainder.add(
                    new IngredientWithRemainder(
                            ingredients.get(i),
                            i < remainder.length && remainder[i],
                            i < durability.length ? durability[i] : 0
                    )
            );
        }

        this.result = result;
    }

    // Convert our custom ingredients to base Minecraft ingredients for parent class
    private static NonNullList<Ingredient> convertToBaseIngredients(NonNullList<IngredientWithRemainder> customIngredients) {
        NonNullList<Ingredient> baseIngredients = NonNullList.create();
        for (IngredientWithRemainder ingredient : customIngredients) {
            baseIngredients.add(ingredient.getIngredient());
        }
        return baseIngredients;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remainingItems = NonNullList.withSize(input.size(), ItemStack.EMPTY);

        // Track which ingredients have been processed
        boolean[] ingredientProcessed = new boolean[ingredientsWithRemainder.size()];

        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack slotStack = input.getItem(slot);
            if (slotStack.isEmpty()) continue;

            // Find matching ingredient with remainder
            for (int ingIndex = 0; ingIndex < ingredientsWithRemainder.size(); ingIndex++) {
                if (!ingredientProcessed[ingIndex] && ingredientsWithRemainder.get(ingIndex).getIngredient().test(slotStack)) {
                    IngredientWithRemainder ingredient = ingredientsWithRemainder.get(ingIndex);

                    if (ingredient.hasRemainder()) {
                        ItemStack remainder = ingredient.getRemainder(slotStack);
                        if (!remainder.isEmpty()) {
                            remainingItems.set(slot, remainder);
                        }
                    }

                    ingredientProcessed[ingIndex] = true;
                    break;
                }
            }
        }

        return remainingItems;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider provider) {
        ItemStack result = super.assemble(input, provider);

        if (!ServerConfig.ENABLE_MINIGAME.get()) {
            // When minigame is disabled
            boolean hasUnpolishedQualityItem = false;
            boolean unquenched = false;
            ForgingQuality foundQuality = null;
            String creator = null;

            for (int i = 0; i < input.size(); i++) {
                ItemStack ingredient = input.getItem(i);

                // Check if item is heated (unquenched)
                if (ingredient.getOrDefault(ModComponents.HEATED_COMPONENT, false)) {
                    unquenched = true;
                    break;
                }

                // Check if item is polished
                Boolean polished = ingredient.get(ModComponents.POLISHED);
                if (polished != null && !polished) {
                    hasUnpolishedQualityItem = true;
                    break;
                }

                // Get forging quality
                ForgingQuality quality = ingredient.get(ModComponents.FORGING_QUALITY);
                if (quality != null && quality != ForgingQuality.NONE) {
                    foundQuality = quality;
                }

                // Get creator
                String itemCreator = ingredient.get(ModComponents.CREATOR);
                if (itemCreator != null) {
                    creator = itemCreator;
                }
            }

            // Prevent crafting if any unpolished quality items exist or item is unquenched
            if (hasUnpolishedQualityItem || unquenched) {
                return ItemStack.EMPTY;
            }

            // Set quality on result
            if (foundQuality == null) {
                foundQuality = ForgingQuality.NONE;
            }
            result.set(ModComponents.FORGING_QUALITY, foundQuality);

            if (creator != null) {
                result.set(ModComponents.CREATOR, creator);
            }

            return result;
        }

        // Original minigame-enabled logic
        ForgingQuality foundQuality = null;
        boolean isPolished = true;
        boolean unquenched = false;
        String creator = null;

        for (int i = 0; i < input.size(); i++) {
            ItemStack ingredient = input.getItem(i);

            // Get forging quality from component
            ForgingQuality quality = ingredient.get(ModComponents.FORGING_QUALITY);
            if (quality != null && quality != ForgingQuality.NONE) {
                foundQuality = quality;
            }

            // Check if polished
            Boolean polished = ingredient.get(ModComponents.POLISHED);
            if (polished != null && !polished) {
                isPolished = false;
            }

            // Check if heated (unquenched)
            if (ingredient.getOrDefault(ModComponents.HEATED_COMPONENT, false)) {
                unquenched = true;
            }

            // Get creator
            String itemCreator = ingredient.get(ModComponents.CREATOR);
            if (itemCreator != null) {
                creator = itemCreator;
            }
        }

        if (foundQuality == null || foundQuality == ForgingQuality.NONE) {
            // If no quality found
            if (!isPolished || unquenched) {
                // Either not polished OR unquenched (or both) → set to POOR
                result.set(ModComponents.FORGING_QUALITY, ForgingQuality.POOR);
            }
            return result;
        } else {
            ForgingQuality quality = foundQuality;

            if (!isPolished) {
                quality = quality.getLowerQuality();
            }
            if (unquenched) {
                quality = quality.getLowerQuality();
            }

            result.set(ModComponents.FORGING_QUALITY, quality);
            if (creator != null) {
                result.set(ModComponents.CREATOR, creator);
            }
            return result;
        }
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.CRAFTING_SHAPELESS.get();
    }

    // Custom ingredient class with remainder support
    public static class IngredientWithRemainder {
        private final Ingredient ingredient;
        private final boolean remainder;
        private final int durabilityDecrease;

        public IngredientWithRemainder(Ingredient ingredient, boolean remainder, int durabilityDecrease) {
            this.ingredient = ingredient;
            this.remainder = remainder;
            this.durabilityDecrease = durabilityDecrease;
        }

        public Ingredient getIngredient() {
            return ingredient;
        }

        public boolean hasRemainder() {
            return remainder;
        }

        public int getDurabilityDecrease() {
            return durabilityDecrease;
        }

        public ItemStack getRemainder(ItemStack original) {
            if (!remainder) return ItemStack.EMPTY;

            ItemStack stack = original.copy();
            stack.setCount(1);

            if (durabilityDecrease > 0 && stack.isDamageableItem()) {
                int newDamage = stack.getDamageValue() + durabilityDecrease;
                if (newDamage >= stack.getMaxDamage()) {
                    return ItemStack.EMPTY;
                }
                stack.setDamageValue(newDamage);
            }

            return stack;
        }
    }


    public static class Serializer implements RecipeSerializer<OvergearedShapelessRecipe> {

        @Override
        public MapCodec<OvergearedShapelessRecipe> codec() {
            return RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.STRING.optionalFieldOf("group", "")
                            .forGetter(OvergearedShapelessRecipe::getGroup),
                    CraftingBookCategory.CODEC
                            .optionalFieldOf("category", CraftingBookCategory.MISC)
                            .forGetter(OvergearedShapelessRecipe::category),
                    ItemStack.CODEC
                            .fieldOf("result")
                            .forGetter(r -> r.result),
                    Ingredient.CODEC_NONEMPTY
                            .listOf()
                            .xmap(
                                    list -> {
                                        NonNullList<Ingredient> nn = NonNullList.create();
                                        nn.addAll(list);
                                        return nn;
                                    },
                                    list -> list
                            )
                            .fieldOf("ingredients")
                            .forGetter(r -> r.getIngredients()),
                    Codec.BOOL.listOf()
                            .optionalFieldOf("remainder", java.util.List.of())
                            .forGetter(r -> r.ingredientsWithRemainder
                                    .stream()
                                    .map(IngredientWithRemainder::hasRemainder)
                                    .toList()),
                    Codec.INT.listOf()
                            .optionalFieldOf("durability_decrease", java.util.List.of())
                            .forGetter(r -> r.ingredientsWithRemainder
                                    .stream()
                                    .map(IngredientWithRemainder::getDurabilityDecrease)
                                    .toList())
            ).apply(instance, (group, category, result, ingredients, remainder, durability) -> {

                boolean[] rem = new boolean[ingredients.size()];
                int[] dur = new int[ingredients.size()];

                for (int i = 0; i < ingredients.size(); i++) {
                    rem[i] = i < remainder.size() && remainder.get(i);
                    dur[i] = i < durability.size() ? durability.get(i) : 0;
                }

                return new OvergearedShapelessRecipe(
                        group,
                        category,
                        result,
                        ingredients,
                        rem,
                        dur
                );
            }));
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, OvergearedShapelessRecipe> streamCodec() {
            return new StreamCodec<>() {
                @Override
                public OvergearedShapelessRecipe decode(RegistryFriendlyByteBuf buf) {
                    String group = buf.readUtf();
                    CraftingBookCategory category = buf.readEnum(CraftingBookCategory.class);
                    ItemStack result = ItemStack.STREAM_CODEC.decode(buf);

                    int ingredientCount = buf.readVarInt();
                    NonNullList<Ingredient> ingredients = NonNullList.create();
                    for (int i = 0; i < ingredientCount; i++) {
                        ingredients.add(Ingredient.CONTENTS_STREAM_CODEC.decode(buf));
                    }

                    boolean[] remainder = new boolean[ingredientCount];
                    for (int i = 0; i < ingredientCount; i++) {
                        remainder[i] = buf.readBoolean();
                    }

                    int[] durability = new int[ingredientCount];
                    for (int i = 0; i < ingredientCount; i++) {
                        durability[i] = buf.readVarInt();
                    }

                    return new OvergearedShapelessRecipe(
                            group,
                            category,
                            result,
                            ingredients,
                            remainder,
                            durability
                    );
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, OvergearedShapelessRecipe recipe) {
                    buf.writeUtf(recipe.getGroup());
                    buf.writeEnum(recipe.category());
                    ItemStack.STREAM_CODEC.encode(buf, recipe.result);

                    int size = recipe.getIngredients().size();
                    buf.writeVarInt(size);

                    for (Ingredient ingredient : recipe.getIngredients()) {
                        Ingredient.CONTENTS_STREAM_CODEC.encode(buf, ingredient);
                    }

                    for (IngredientWithRemainder iwr : recipe.ingredientsWithRemainder) {
                        buf.writeBoolean(iwr.hasRemainder());
                    }

                    for (IngredientWithRemainder iwr : recipe.ingredientsWithRemainder) {
                        buf.writeVarInt(iwr.getDurabilityDecrease());
                    }
                }
            };
        }
    }
}