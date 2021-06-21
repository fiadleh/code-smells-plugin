package com.github.fiadleh.codesmellsplugin.codesmells.dataclumps;

import com.github.fiadleh.codesmellsplugin.services.CodesmellTimer;
import com.github.fiadleh.codesmellsplugin.services.PsiGroup;
import com.github.fiadleh.codesmellsplugin.util.PsiUtils;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Responsible for executing refactoring actions for data clumps instances
 *
 * @author Firas Adleh
 */
public class DataclassRefactoringService {
    /**
     * An identifier used to mark the lines made by this class in log
     */
    private static final String LOGGER_NAME = "DataclumpsRefactoring";
    private static final CodesmellTimer refactoringTimer = new CodesmellTimer("Dataclumps refactoring");

    private static Project project;

    private DataclassRefactoringService() {
    }

    /**
     * Extract textual information about the instance elements from the current data clumps instance
     * @param currentDataClump      current data clumps instance
     * @return                      textual information
     */
    private static ArrayList<String> prepareElementsText(PsiGroup currentDataClump) {
        final boolean isParameterDataclump = currentDataClump.getElement(0) instanceof PsiParameter;
        ArrayList<String> elementsText = new ArrayList<>();

        for (PsiElement elem : currentDataClump.getElements()) {
            if (isParameterDataclump) {
                elementsText.add(((PsiParameter) elem).getType().getCanonicalText() + " " + ((PsiParameter) elem).getName());
            } else {
                elementsText.add(((PsiField) elem).getType().getCanonicalText() + " " + ((PsiField) elem).getName());
            }
        }

        return elementsText;
    }

    /**
     * Extract textual information about the files affected by the current data clumps instance
     * @param currentDataClump      current data clumps instance
     * @return                      textual information
     */
    private static ArrayList<String> prepareFilesText(PsiGroup currentDataClump) {
        final boolean isParameterDataclump = currentDataClump.getElement(0) instanceof PsiParameter;
        ArrayList<String> filesText = new ArrayList<>();

        for (PsiElement elem : currentDataClump.getConnections()) {
            if (isParameterDataclump) {
                PsiClass containingClass = ((PsiMethod) elem.getParent()).getContainingClass();
                if(containingClass != null) {
                    filesText.add("Method: " + ((PsiMethod) elem.getParent()).getName() + " in class: " + containingClass.getName() + " in file: " + elem.getParent().getContainingFile().getVirtualFile().getUrl());
                }
            } else {
                filesText.add("Class: " + ((PsiClass) elem).getName() + " in file " + elem.getContainingFile().getVirtualFile().getUrl());
            }
        }

        return filesText;
    }

    /**
     * The first called method to start the refactoring process.
     *
     * @param currentProject
     * @param currentDataClump
     */
    public static void refactor(@NotNull Project currentProject, @NotNull PsiGroup currentDataClump) {
        PsiUtils.log(LOGGER_NAME, "refactor: ");
        project = currentProject;
        // create a new class name out of its variables
        String className = PsiUtils.generateClassNameFromVariables(currentDataClump.getElements());
        String qualifiedName = "";

        final boolean isParameterDataclump = currentDataClump.getElement(0) instanceof PsiParameter;
        ArrayList<String> elementsText = prepareElementsText(currentDataClump);
        ArrayList<String> filesText = prepareFilesText(currentDataClump);
        int useExistingClassResult = 2;

        PsiClass alreadyExtractedClass = PsiUtils.findParameterObject(project, currentDataClump.getElements());
        if (alreadyExtractedClass != null) {
            // show an info box
            useExistingClassResult = Messages.showOkCancelDialog(project,
                    "There is already a class with all required variables (" + alreadyExtractedClass.getQualifiedName() + "), Do you want to replace the duplicate variables with it? ",
                    "Data Clumps Refactoring Info", "Yes, Use This Class", "No, I Want to Extract to a New Class",
                    Messages.getInformationIcon());
            PsiUtils.log(LOGGER_NAME, "useExistingClassResult: " + useExistingClassResult);
        }
        final boolean useExistingClass = (useExistingClassResult == 0);

        if (useExistingClass) {
            className = alreadyExtractedClass.getName();
            qualifiedName = alreadyExtractedClass.getQualifiedName();
        }
        // create a refactoring dialog to choose new class name
        String nameCheckMessage = "Please choose a name for the new class:";

        while (!nameCheckMessage.equals("OK") && !useExistingClass) {
            DataclumpRefactoringDialog dataclumpsRefactoringDialog = new DataclumpRefactoringDialog(className, nameCheckMessage, elementsText, filesText);
            if (dataclumpsRefactoringDialog.showAndGet()) {
                // user pressed OK
                className = dataclumpsRefactoringDialog.getNewClassName();

                // check that creating a new class with this name is allowed
                nameCheckMessage = PsiUtils.checkClassExists(currentDataClump.getConnections().get(0).getContainingFile().getContainingDirectory(), className);


                PsiUtils.log(LOGGER_NAME, "user pressed OK");
                PsiUtils.log(LOGGER_NAME, "Name = " + className);
                PsiUtils.log(LOGGER_NAME, "Directory = " + currentDataClump.getConnections().get(0).getContainingFile().getContainingDirectory().getText());
                PsiUtils.log(LOGGER_NAME, "nameCheckMessage = " + nameCheckMessage);

            } else {
                className = dataclumpsRefactoringDialog.getNewClassName();

                PsiUtils.log(LOGGER_NAME, "DataClumpFix Cancel !!!!!!!!!!!!");
                PsiUtils.log(LOGGER_NAME, "Name = " + className);
                PsiUtils.log(LOGGER_NAME, "Directory = " + currentDataClump.getConnections().get(0).getContainingFile().getContainingDirectory().getText());

                return;
            }
            if (!nameCheckMessage.equalsIgnoreCase("OK")) {
                Messages.showMessageDialog(project, nameCheckMessage, "Error Creating New Class", Messages.getErrorIcon());
            }
        }

        try {
            // start timer
            refactoringTimer.startTimer();

            refactoringTimer.setClassName(className);
            final PsiGroup finalDataClump = currentDataClump;
            final String finalClassName = className;
            final String finalClassQualifiedName = qualifiedName;


            WriteCommandAction.runWriteCommandAction(project, "Dataclump Refactoring Service: " + className, "0", () -> {
                String newClassQualifiedName = finalClassQualifiedName;

                //======== create a new class if required =========
                if (!useExistingClass) {
                    newClassQualifiedName = PsiUtils.extractVariablesClass(project, finalDataClump.getConnections().get(0).getContainingFile().getContainingDirectory(), finalClassName, finalDataClump.getElements());
                }
                if (isParameterDataclump) {
                    //============== refactor the calls in all project classes =============
                    preserveWholeObjectInMethodsCalls(finalDataClump, newClassQualifiedName);

                    //============== add the new class to the signature and body of the smelly methods in the original class =============
                    preserveWholeObjectInOriginalClasses(finalDataClump, finalClassName, newClassQualifiedName);
                } else {
                    //============== refactor the calls in all project classes =============
                    refactorFieldCalls(finalDataClump, newClassQualifiedName);

                    //============== delete the smelly fields from the affected classes =============
                    removeSmellyFields(finalDataClump, newClassQualifiedName);
                }
            });

            // STOP TIMER AND PRINT THE RESULTS
            refactoringTimer.stopTimer();
            refactoringTimer.printMessage();
        } catch (PsiInvalidElementAccessException | IncorrectOperationException e) {
            PsiUtils.log(LOGGER_NAME, "\n==> Exception: " + e.getMessage() + "\n");
        }
    }

    /**
     * delete the smelly parameters from signature and body of the smelly methods
     * and add the new class instead
     *
     * @param currentDataClump
     * @param className
     * @param classQualifiedName
     */
    public static void preserveWholeObjectInOriginalClasses(PsiGroup currentDataClump, String className, String classQualifiedName) {
        Logger.getInstance("#DataclumpsInspection").warn("\nrefactorParameterListMethods: ");
        PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
        for (PsiElement connection : currentDataClump.getConnections()) {
            // 1. Search for smelly parameters in the current method
            PsiParameterList currentPsiParameterList = (PsiParameterList) connection;
            PsiParameter[] parameters = currentPsiParameterList.getParameters();
            int paramCount = currentPsiParameterList.getParametersCount();
            List<Integer> smellyParametersIndices = new ArrayList<>();
            List<PsiElement> smellyParameters = new ArrayList<>();

            for (int x = 0; x < paramCount; x++) {
                int parameterIndexInDataclump = PsiUtils.getElementIndex(currentDataClump.getElements(), currentPsiParameterList.getParameter(x));

                if (parameterIndexInDataclump != -1) {
                    smellyParametersIndices.add(x);
                    smellyParameters.add(parameters[x].getNameIdentifier());
                }
            }

            // 2. refactor the smelly parameters in method body
            PsiMethod currentMethod = (PsiMethod) currentPsiParameterList.getParent();
            ArrayList<PsiReferenceExpression> refs = new ArrayList<>(
                    PsiTreeUtil.findChildrenOfType(currentMethod.getBody(), PsiReferenceExpression.class));
            // reverse the list order to refactor the inner references first if one reference should be assigned to the other
            Collections.reverse(refs);
            for (PsiReferenceExpression ref : refs) {
                int parameterIndexInDataclump = PsiUtils.getElementIndex(smellyParameters, ref);
                if (parameterIndexInDataclump != -1) {
                    // refactor this reference
                    PsiUtils.refactorReferenceToSetterGetter(project, ref, "m" + className, ref.getText());
                }

            }

            // 3. Delete the smelly parameters from method signature
            for (int i : smellyParametersIndices) {
                parameters[i].delete();
            }
            // 4. add the class containing the smelly parameters to method signature
            currentPsiParameterList.addBefore(factory.createParameter(
                    "m" + className, factory.createTypeFromText(classQualifiedName, null)), currentPsiParameterList.getParameter(0));

            // add an import for the new class if required
            JavaCodeStyleManager.getInstance(project).shortenClassReferences(currentMethod.getContainingFile());
        }
    }

    /**
     * search for all calls of the smelly methods in all project classes and refactor them
     * to use the new class name instead of the smelly parameters or fields
     *
     * @param currentDataClump
     * @param className
     */
    public static void preserveWholeObjectInMethodsCalls(PsiGroup currentDataClump, String className) {


        // go through all methods affected by this data clump instance
        for (PsiElement connection : currentDataClump.getConnections()) {
            // search for each call for the current method in this project
            Collection<PsiReference> allReferences = ReferencesSearch.search(connection.getParent(), GlobalSearchScope.projectScope(project)).findAll();
            PsiUtils.log(LOGGER_NAME, "allReferences: " + allReferences.size());


            for (PsiReference ref : allReferences) {
                // refactor the affected call to use the new class instead of the data clump variables
                if (PsiTreeUtil.instanceOf(ref.getElement().getParent(), PsiMethodCallExpression.class)) {
                    refactorCall(currentDataClump, className, (PsiParameterList) connection, (PsiMethodCallExpression) ref.getElement().getParent());
                } else {
                    PsiUtils.log(LOGGER_NAME, "       §§§§§§§§§§§§§   not a PsiMethodCallExpression: " + ref + " - file: " + ref.getElement().getContainingFile().getContainingDirectory() + ref.getElement().getContainingFile());
                }
            }
        }
    }

    /**
     * Refactor the affected call to use the new class instead of the data clump variables
     * @param currentDataClump
     * @param className
     * @param paramList
     * @param call
     */
    private static void refactorCall(PsiGroup currentDataClump, String className, PsiParameterList paramList, PsiMethodCallExpression call) {
        PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();


        String[] constructorArguments = new String[currentDataClump.getElements().size()];

        PsiExpressionList expList = call.getArgumentList();
        PsiExpression[] parametersExpressions = expList.getExpressions();


        List<Integer> parameterIndexesToDelete = new ArrayList<>();
        // collect parameter values from the current call to be added to the constructor
        for (int x = 0; x < paramList.getParametersCount(); x++) {
            int parameterIndexInDataclump = PsiUtils.getElementIndex(currentDataClump.getElements(), paramList.getParameter(x));

            if (parameterIndexInDataclump != -1) {
                constructorArguments[parameterIndexInDataclump] = call.getArgumentList().getExpressions()[x].getText();
                parameterIndexesToDelete.add(x);
            }
        }
        // delete the parameter from method call
        for (int i : parameterIndexesToDelete) {
            parametersExpressions[i].delete();
        }
        // add the new constructor with the deleted parameters
        if (expList.getExpressions().length > 0) {
            // add the new class as the first parameter if there are other parameters in the call
            expList.addBefore(factory.createExpressionFromText("new " + className + "(" + String.join(",", constructorArguments) + ")", null),
                    expList.getExpressions()[0]);
        } else {
            // just add the new class as the only parameter if all other parameters have been deleted
            expList.add(factory.createExpressionFromText("new " + className + "(" + String.join(",", constructorArguments) + ")", null));
        }

        // add an import for the new class if required
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(call.getContainingFile());
        PsiUtils.log(LOGGER_NAME, "Refactored: " + call + " - file: " + call.getContainingFile().getContainingDirectory() + call.getContainingFile());
    }

    /**
     * Go through all fields affected by this data clump instance in every affected class and refactor them
     *
     * @param currentDataClump
     * @param fullClassName
     */
    public static void refactorFieldCalls(PsiGroup currentDataClump, String fullClassName) {
        PsiUtils.log(LOGGER_NAME, "------------------ refactorFieldCallsInProject -----------------");

        // go through all fields affected by this data clump instance in every affected class
        for (PsiElement field : currentDataClump.getElements()) {
            for (PsiElement dataclumpClass : currentDataClump.getConnections()) {
                for (PsiField currentField : ((PsiClass) dataclumpClass).getFields()) {
                    if (((PsiField) field).getName().equals(currentField.getName())) {
                        PsiUtils.log(LOGGER_NAME, "Refactor Field: " + currentField.getName() + " in class " + ((PsiClass) dataclumpClass).getName());

                        // search for each call for the current method in this project
                        Collection<PsiReference> allReferences = ReferencesSearch.search(currentField, GlobalSearchScope.projectScope(project)).findAll();
                        PsiUtils.log(LOGGER_NAME, "allReferences: " + allReferences.size());
                        String fieldName = currentField.getName();

                        for (PsiReference ref : allReferences) {
                            refactorOneFieldCall(fullClassName, ref, fieldName);
                        }
                    }
                }
            }
        }
    }

    /**
     * Refactor one call of one field
     *
     * @param fullClassName
     * @param ref
     * @param fieldName
     */
    public static void refactorOneFieldCall(String fullClassName, PsiReference ref, String fieldName) {
        String className = fullClassName.substring(fullClassName.lastIndexOf('.') + 1);
        PsiClass refClass = PsiUtil.getTopLevelClass(ref.getElement());
        if (refClass !=null && refClass.getQualifiedName()!=null && !refClass.getQualifiedName().equals(fullClassName)) {
            PsiUtils.refactorReferenceToSetterGetter(project, ref, "m" + className, fieldName);
        }
    }

    /**
     * Remove all fields affected by this data clump instance in all affected classes
     *
     * @param currentDataClump
     * @param fullClassName
     */
    public static void removeSmellyFields(PsiGroup currentDataClump, String fullClassName) {
        PsiUtils.log(LOGGER_NAME, "--- removeSmellyFields ---");

        // go through all fields affected by this data clump instance in every affected class
        for (PsiElement dataclumpClass : currentDataClump.getConnections()) {
            removeSmellyFieldsFromOneClass(currentDataClump, fullClassName, dataclumpClass);
        }
    }

    /**
     * Remove all fields affected by this data clump instance in one class
     *
     * @param currentDataClump
     * @param fullClassName
     * @param dataclumpClass
     */
    private static void removeSmellyFieldsFromOneClass(PsiGroup currentDataClump, String fullClassName, PsiElement dataclumpClass) {
        PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
        String className = fullClassName.substring(fullClassName.lastIndexOf('.') + 1);
        String[] constructorsParams = new String[currentDataClump.getElements().size()];
        HashMap<String, String> fieldsValues = new HashMap<>();
        for (PsiElement field : currentDataClump.getElements()) {
            fieldsValues.put(((PsiField) field).getName(), PsiUtils.getDefaultValue(((PsiField) field).getType().getPresentableText()));
        }
        // remove the smelly fields
        for (PsiField currentField : ((PsiClass) dataclumpClass).getFields()) {
            for (PsiElement field : currentDataClump.getElements()) {
                if (((PsiField) field).getName().equals(currentField.getName())) {
                    if (currentField.getInitializer() != null) {
                        fieldsValues.put(((PsiField) field).getName(), currentField.getInitializer().getText());
                    }
                    currentField.delete();
                }
            }
        }
        // add the new class field with the given values form other fields or the default values
        int i = 0;
        for (PsiElement field : currentDataClump.getElements()) {
            constructorsParams[i] = fieldsValues.get(((PsiField) field).getName());
            i++;
        }
        PsiField field = factory.createFieldFromText("public " + fullClassName +
                " m" + className + " = new " + fullClassName +
                "(" + StringUtils.join(constructorsParams, ", ") + ");", null);
        dataclumpClass.add(field);

        // add an import for the new class if required
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(dataclumpClass.getContainingFile());
    }
}
