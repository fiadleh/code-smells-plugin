package com.github.fiadleh.codesmellsplugin.codesmells.globaldata;

import com.github.fiadleh.codesmellsplugin.util.PsiUtils;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Objects;

/**
 * Receives the detected global data instance and refactor it
 *
 * @author Firas Adleh
 */
public class GlobalDataFix implements LocalQuickFix {
    /**
     * Text to be displayed in problem description
     */
    public static final String QUICK_FIX_NAME = "Encapsulate the global variable by adding get and set methods.";

    /**
     * An identifier used to mark the lines made by this class in log
     */
    private static final Logger LOG = Logger.getInstance("#GlobalDataRefactoring");


    /**
     * This is called when starting the refactoring
     * @param project
     * @param descriptor
     */
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        // call the refactoring method for global data
        ApplicationManager.getApplication().invokeLater(
                () -> GlobalDataFix.refactor(project, descriptor.getPsiElement()));
    }

    /**
     * The main refactoring method
     * @param project
     * @param fieldPsiElement
     */
    public static void refactor(@NotNull Project project, @NotNull PsiElement fieldPsiElement) {
        LOG.warn(" ++ GlobalDataFix.refactor");
        String className = Objects.requireNonNull(((PsiField) fieldPsiElement).getContainingClass()).getQualifiedName();
        String fieldName = ((PsiField) fieldPsiElement).getName();
        LOG.warn("Class: " + fieldPsiElement.getParent());
        LOG.warn("fieldPsiElement: " + fieldPsiElement.getClass());
        Collection<PsiReference> allReferences = ReferencesSearch.search(fieldPsiElement, GlobalSearchScope.projectScope(project)).findAll();
        LOG.warn("allReferences: " + allReferences.size());


        WriteCommandAction.runWriteCommandAction(project, "Global Data Refactoring Action: " + className, "0", () -> {
            // create getter and setter methods for this field
            PsiMethod getMethod = PsiUtils.createGetterSetterMethod(true, project, fieldName, ((PsiField) fieldPsiElement).getType(), false, false, true);
            Objects.requireNonNull(((PsiField) fieldPsiElement).getContainingClass()).add(getMethod);
            PsiMethod setMethod = PsiUtils.createGetterSetterMethod(false, project, fieldName, ((PsiField) fieldPsiElement).getType(), false, false, true);
            Objects.requireNonNull(((PsiField) fieldPsiElement).getContainingClass()).add(setMethod);

            // change field to private
            Objects.requireNonNull(((PsiField) fieldPsiElement).getModifierList()).setModifierProperty(PsiModifier.PRIVATE, true);


            // refactor all calls to this field
            for (PsiReference ref : allReferences) {
                // don't refactor calls in the same class
                if (!Objects.equals(Objects.requireNonNull(PsiUtil.getTopLevelClass(ref.getElement())).getQualifiedName(), className)) {
                    PsiUtils.refactorReferenceToSetterGetter(project, ref, className, fieldName);

                }
            }

            // show an info box
            ApplicationManager.getApplication().invokeLater(
                    () -> Messages.showMessageDialog(project, "Automatic refactoring done! " + allReferences.size() + " references have been refactored", "Global Data Refactoring Info", Messages.getInformationIcon()));

        });
    }

    @NotNull
    public String getFamilyName() {
        return QUICK_FIX_NAME;
    }
}
