package com.github.fiadleh.codesmellsplugin.codesmells.globaldata;

import com.github.fiadleh.codesmellsplugin.services.CodesmellTimer;
import com.github.fiadleh.codesmellsplugin.util.PsiUtils;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Main inspection class for global data. Responsible for starting checks and reporting found instances.
 *
 * @author Firas Adleh
 */
public class GlobalDataInspection extends LocalInspectionTool {


    /**
     * An identifier used to mark the lines made by this class in log
     */
    private static final String GROUP_DISPLAY_NAME = "Code Smells";

    /**
     * Group name in inspections configuration window
     */
    private static final String CODE_SMELL_DISPLAY_NAME = "Global Data";

    /**
     * The refactoring class for global data instances
     */
    private final GlobalDataFix globalDataFix = new GlobalDataFix();


    @Override
    @NotNull
    public String getGroupDisplayName() {
        return GROUP_DISPLAY_NAME;
    }

    @Override
    @NotNull
    public String getDisplayName() {
        return CODE_SMELL_DISPLAY_NAME;
    }


    /**
     * This is called automatically when the user edits code or lunches a custom inspection scan.
     * An instance of JavaElementVisitor is created here to visit the different code parts.
     * There is a problem holder that receives detected global data instances as problems.
     *
     * @param holder
     * @param isOnTheFly
     * @return
     */
    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
        return new JavaElementVisitor() {
            /**
             * The message to be displayed to describe the problem.
             */
            @NonNls
            private static final String DESCRIPTION_TEMPLATE = "Code Smell (Global Data) --------> : ";

            @Override
            public void visitField(PsiField field) {
                if (Objects.requireNonNull(field.getModifierList()).hasExplicitModifier(PsiModifier.PUBLIC) && field.getModifierList().hasExplicitModifier(PsiModifier.STATIC)
                        && !field.getModifierList().hasExplicitModifier(PsiModifier.FINAL)) {
                    PsiUtils.log(CODE_SMELL_DISPLAY_NAME, "field: " + field + ", ModifierList= " + field.getModifierList() + ", parent=" + field.getParent());
                    if (field.getSourceElement() != null) {
                        holder.registerProblem(field.getSourceElement(),
                                DESCRIPTION_TEMPLATE + " in class(" + Objects.requireNonNull(field.getContainingClass()).getName() + "): ", globalDataFix);
                    }
                }
            }

        };
    }
}