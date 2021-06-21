package com.github.fiadleh.codesmellsplugin.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.*;

/**
 * Utilities is a collection of reusable functions for detection and refactoring.
 *
 * @author Firas Adleh
 */
public class PsiUtils {


    /**
     * A logger for printing debugging messages in the main IntelliJ log.
     */
    public static final Logger mainLog = Logger.getInstance("#CodeSmells");

    /**
     * A flag for activating debug mode which print messages through the logger
     */
    private static final boolean DEBUG_MODE = false;


    public static boolean isDebugMode() {
        return DEBUG_MODE;
    }


    private PsiUtils() {
    }

    /**
     * Prints a message by the logger
     *
     * @param message
     */
    public static void log(String message) {
        if (DEBUG_MODE) {
            mainLog.warn(message);
        }
    }

    /**
     * Prints a message by the logger using a suffix to group the messages
     *
     * @param message
     */
    public static void log(String loggerInstance, String message) {
        if (DEBUG_MODE) {
            Logger.getInstance("#" + loggerInstance).warn(message);
        }
    }

    /**
     * Searches for an element in a list matching only the text and not the actual object
     * returns element index if found otherwise -1
     *
     * @param elementsList  a list of elements to search in
     * @param element       the wanted element
     * @return              element index if found otherwise -1
     */
    public static int getElementIndex(List<PsiElement> elementsList, PsiElement element) {
        for (int i = 0; i < elementsList.size(); i++) {
            if (elementsList.get(i).getText().equals(element.getText())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Extracts variables to a new class and return its full qualified name (with package)
     *
     * @param project   current project
     * @param directory path to create the new class in
     * @param className name of the new class
     * @param variables a list of parameters or fields
     * @return class classQualifiedName
     */
    public static String extractVariablesClass(Project project, PsiDirectory directory, String className, List<PsiElement> variables) {
        PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
        PsiClass newClass = JavaDirectoryService.getInstance().createClass(directory, className);


        String[] constructorParametersNames = new String[variables.size()];
        String[] constructorAssignmentsLines = new String[variables.size()];


        for (int i = 0; i < variables.size(); i++) {
            // create fields
            PsiType type = null;
            String name = "";
            PsiField field = null;

            if (variables.get(i) instanceof com.intellij.psi.impl.source.PsiParameterImpl) {
                type = factory.createTypeFromText(variables.get(i).getChildren()[1].getText(), variables.get(i).getChildren()[1]);
                name = variables.get(i).getChildren()[3].getText();
                field = factory.createField(name, type);
            } else if (variables.get(i) instanceof com.intellij.psi.impl.source.PsiFieldImpl) {
                type = ((PsiField) variables.get(i)).getType();

                name = ((PsiField) variables.get(i)).getName();
                field = factory.createField(name, type);
            } else {
                Logger.getInstance("#DataclumpsRefactoring").warn("in extractVariablesClass() Unknown type: " + variables.get(i).getClass().getName());
            }
            if (field != null) {
                newClass.add(field);


                constructorParametersNames[i] = type.getCanonicalText() + " " + name;
                constructorAssignmentsLines[i] = "this." + name + " = " + name + ";";

                // create a getter method for the field
                PsiMethod getMethod = factory.createMethod("get" + name, type);
                PsiCodeBlock getMethodCodeBlock = factory.createCodeBlockFromText("{ return this." + name + "; }", null);
                Objects.requireNonNull(getMethod.getBody()).replace(getMethodCodeBlock);
                newClass.add(getMethod);

                // create a setter method for the field
                PsiMethod setMethod = PsiUtils.createGetterSetterMethod(false, project, name, type, false, false, false);
                newClass.add(setMethod);
            }
        }
        // create the constructor

        PsiMethod constructor = factory.createMethodFromText("public " + className + "(" + String.join(", ", constructorParametersNames) + ")", null);
        Logger.getInstance("#DataclumpsRefactoring").warn("extract class " + className + " for vars: " + String.join(", ", constructorParametersNames) + "");
        PsiCodeBlock constructorMethodCodeBlock = factory.createCodeBlockFromText("{ \n" + String.join("\n", constructorAssignmentsLines) + "\n}", null);
        constructor.add(constructorMethodCodeBlock);
        PsiMethod defaultConstructor = factory.createMethodFromText("public " + className + "(){}", null);

        newClass.add(constructor);
        newClass.add(defaultConstructor);

        // reformat the class file with IntelliJ style manager (whitespaces, indents)
        CodeStyleManager styleManager = CodeStyleManager.getInstance(project);
        styleManager.reformat(newClass.getContainingFile());

        return newClass.getQualifiedName();
    }

    /**
     * Check if it is ok to create a class in a given directory
     *
     * @param directory
     * @param className
     * @return              a string "OK" if no problem, otherwise the problem's text
     */
    public static String checkClassExists(PsiDirectory directory, String className) {
        String result = "OK";
        try {
            JavaDirectoryService.getInstance().checkCreateClass(directory, className);
        } catch (IncorrectOperationException e) {
            result = e.getMessage();
        }
        return result;
    }

    /**
     * Makes a long name by combining all variable names
     *
     * @param variables
     * @return
     */
    public static String generateClassNameFromVariables(List<PsiElement> variables) {
        StringBuilder classNameBuilder = new StringBuilder();
        for (PsiElement variable : variables) {
            if (variable instanceof PsiParameter) {
                classNameBuilder.append(((PsiParameter) variable).getName());
            }
            if (variable instanceof PsiField) {
                classNameBuilder.append(((PsiField) variable).getName());
            }
        }
        return classNameBuilder.toString();
    }

    /**
     * Create a setter or a getter PSI method for a given variable name
     *
     * @param isGetter
     * @param project
     * @param variableName
     * @param type
     * @param isProtected
     * @param isPrivate
     * @param isStatic
     * @return
     */
    public static PsiMethod createGetterSetterMethod(boolean isGetter, Project project, String variableName, PsiType type, boolean isProtected, boolean isPrivate, boolean isStatic) {

        // create a setter method for the variable
        PsiMethod createdMethod = JavaPsiFacade.getInstance(project).getElementFactory().createMethodFromText("public void set" + variableName + "()", null);
        PsiCodeBlock methodCodeBlock = JavaPsiFacade.getInstance(project).getElementFactory().createCodeBlockFromText("{ " + variableName + " = newValue; }", null);
        createdMethod.add(methodCodeBlock);

        if (isGetter) {
            // create a getter method for the variable
            createdMethod = JavaPsiFacade.getInstance(project).getElementFactory().createMethod("get" + variableName, type);
            methodCodeBlock = JavaPsiFacade.getInstance(project).getElementFactory().createCodeBlockFromText("{ return " + variableName + "; }", null);
            Objects.requireNonNull(createdMethod.getBody()).replace(methodCodeBlock);
        }

        createdMethod.getModifierList().setModifierProperty(PsiModifier.PROTECTED, isProtected);
        createdMethod.getModifierList().setModifierProperty(PsiModifier.PRIVATE, isPrivate);
        createdMethod.getModifierList().setModifierProperty(PsiModifier.STATIC, isStatic);
        createdMethod.getParameterList().add(JavaPsiFacade.getInstance(project).getElementFactory().createParameter("newValue", type));
        return createdMethod;
    }

    /**
     * Counts common parameters between two parameters lists
     *
     * @param list1
     * @param list2
     * @param debug         activates debuging for this method
     * @return
     */
    public static int countCommonParameters(PsiParameterList list1, PsiParameterList list2, boolean debug) {
        int countCommonParameters = 0;
        if (debug) Logger.getInstance("#countCommonParameters  ").warn(" ******* ");
        for (PsiParameter elem1 : list1.getParameters()) {
            for (PsiParameter elem2 : list2.getParameters()) {
                if (debug) {
                    Logger.getInstance("#countCommonParameters  ").warn("p1: " + elem1.getText() + ", p2: " + elem2.getText() + ", result=" + (elem1.getText().equals(elem2.getText())));
                }
                if (elem1.getText().equalsIgnoreCase(elem2.getText())) {
                    countCommonParameters++;
                    break;
                }
            }
        }
        return countCommonParameters;
    }

    /**
     * Collect the common parameters between two parameter lists is a new list and return it
     *
     * @param list1
     * @param list2
     * @return
     */
    public static List<PsiParameter> getCommonParameters(PsiParameterList list1, PsiParameterList list2) {
        List<PsiParameter> commonParameters = new ArrayList<>();
        for (PsiParameter elem1 : list1.getParameters()) {
            for (PsiParameter elem2 : list2.getParameters()) {
                if (elem1.getText().equalsIgnoreCase(elem2.getText())
                ) {
                    commonParameters.add(elem1);
                    break;
                }
            }
        }
        return commonParameters;
    }

    /**
     * Counts common fields between two classes
     *
     * @param class1
     * @param class2
     * @return
     */
    public static int countCommonFields(PsiClass class1, PsiClass class2) {
        int countCommonFields = 0;
        for (PsiField field1 : class1.getFields()) {
            String type1 = field1.getType().toString();

            for (PsiField field2 : class2.getFields()) {
                String type2 = field2.getType().toString();

                if (Objects.requireNonNull(field1.getModifierList()).toString().equalsIgnoreCase(Objects.requireNonNull(field2.getModifierList()).toString()) &&
                        type1.equalsIgnoreCase(type2) &&
                        field1.getName().equalsIgnoreCase(field2.getName())
                ) {

                    countCommonFields++;
                    break;
                }
            }
        }
        return countCommonFields;
    }

    /**
     * Counts common fields between a class and a parameter list
     *
     * @param class1
     * @param methodParameterList
     * @return
     */
    public static int countCommonFields(PsiClass class1, PsiParameterList methodParameterList) {
        int countCommonFields = 0;
        if (class1 != null && class1.isValid()) {
            for (PsiField field1 : class1.getFields()) {
                String type1 = field1.getType().toString();

                for (PsiParameter param : methodParameterList.getParameters()) {
                    String type2 = param.getType().toString();

                    if (type1.equalsIgnoreCase(type2) &&
                            field1.getName().equalsIgnoreCase(param.getName())
                    ) {

                        countCommonFields++;
                        break;
                    }
                }
            }
        }
        return countCommonFields;
    }

    /**
     * Collect the common fields between two classes is a new list and return it
     *
     * @param class1
     * @param class2
     * @return
     */
    public static List<PsiField> getCommonFields(PsiClass class1, PsiClass class2) {
        List<PsiField> results = new ArrayList<>();
        for (PsiField field1 : class1.getFields()) {
            String type1 = field1.getType().toString();

            for (PsiField field2 : class2.getFields()) {
                String type2 = field2.getType().toString();

                if (Objects.requireNonNull(field1.getModifierList()).toString().equals(Objects.requireNonNull(field2.getModifierList()).toString()) &&
                        type1.equals(type2) &&
                        field1.getName().equals(field2.getName())
                ) {
                    results.add(field1);
                    break;
                }
            }

        }
        return results;
    }

    /**
     * Checks if two classes have a common super class or interface
     *
     * @param class1
     * @param class2
     * @return
     */
    public static boolean hasCommonHierarchy(PsiClass class1, PsiClass class2) {
        if (class2.getName() == null || class1.getName() == null) {
            return false;
        }
        List<String> allSuper1 = CacheManager.getSuperClasses(class1);
        List<String> allSuper2 = CacheManager.getSuperClasses(class2);


        if (allSuper1.isEmpty() || allSuper2.isEmpty()) {
            return false;
        }

        for (String sup1 : allSuper1) {
            if (allSuper2.contains(sup1)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find the PSI class using string contains the class type
     *
     * @param project
     * @param classType
     * @return
     */
    public static Collection<PsiClass> getClassFromType(Project project, String classType) {
        return com.intellij.psi.search.searches.AllClassesSearch.search(GlobalSearchScope.projectScope(project),
                project,
                classType::equals).findAll();
    }

    public static String getDefaultValue(String type) {
        String[] primitiveNumbers = {"byte", "short", "int", "long", "float", "double"};
        if (Arrays.asList(primitiveNumbers).contains(type)) {
            return "0";
        }
        if (type.equals("boolean")) {
            return "false";
        }
        if (type.equals("char")) {
            return "0";
        }
        return "null";
    }


    /**
     * Search the project for a class that has only these fields with setters and getters
     *
     * @param requiredFields a list of the required fields
     * @return psi class if a proper class is found otherwise null
     */
    public static PsiClass findParameterObject(Project currentProject, List<PsiElement> requiredFields) {
        for (PsiClass c : CacheManager.getAllClasses(currentProject)) {
            if (c.isValid() && hasClassAllFields(requiredFields, c)) {
                return c;
            }
        }
        return null;
    }

    /**
     * Checks if the given class has all the required fields
     *
     * @param requiredFields
     * @param currentClass
     * @return
     */
    private static boolean hasClassAllFields(List<PsiElement> requiredFields, PsiClass currentClass) {
        if (currentClass.getFields().length < requiredFields.size()) {
            return false;
        }

        boolean allFieldsFound = true;
        for (PsiField field : currentClass.getFields()) {
            boolean currentFieldsFound = false;
            for (PsiElement reqField : requiredFields) {
                if (reqField instanceof PsiParameter &&
                        ((PsiParameter) reqField).getName().equalsIgnoreCase(field.getName())
                        && ((PsiParameter) reqField).getType().getCanonicalText().equals(field.getType().getCanonicalText())
                        // check getter and setter
                        && currentClass.findMethodsByName("get" + field.getName(), true).length > 0
                        && currentClass.findMethodsByName("set" + field.getName(), true).length > 0
                ) {
                    currentFieldsFound = true;
                    break;

                }
            }
            if (!currentFieldsFound) {
                allFieldsFound = false;
                break;
            }
        }
        if (allFieldsFound) {
            log(currentClass.getName() + " ALl Found !!!!! " + requiredFields.toString().toLowerCase());

            return true;
        }

        return false;
    }

    /***
     * Replaces the giving reference with either a getter or setter method according to its parent expression
     *
     * @param project           current project
     * @param reference         reference to be replaced
     * @param className         the class that contains the reference as a field
     * @param variableName      name of the field in the class
     */
    public static void refactorReferenceToSetterGetter(Project project, PsiReference reference, String className, String variableName) {
        PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
        PsiExpression getterExpression = factory.createExpressionFromText(className +
                ".get" + variableName + "()", null);

        if (reference.getElement().getParent() instanceof com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl) {
            PsiUtils.log("Reference Expression, class = " + Objects.requireNonNull(PsiUtil.getTopLevelClass(reference.getElement())).getQualifiedName());

            reference.getElement().replace(getterExpression);
        } else if (reference.getElement().getParent() instanceof com.intellij.psi.impl.source.tree.java.PsiAssignmentExpressionImpl) {
            PsiUtils.log("Assignment Expression, class = " + Objects.requireNonNull(PsiUtil.getTopLevelClass(reference.getElement())).getQualifiedName());
            PsiAssignmentExpression exp = (PsiAssignmentExpression) reference.getElement().getParent();
            // check if the reference on the left side of assignment expression
            if (PsiTreeUtil.isAncestor(exp.getLExpression(), reference.getElement(), false)) {
                // on the left side means a new value is assigned and this requires a setter call
                PsiUtils.log("Setter: " + exp);
                PsiUtils.log("result: " + className + ".set" + variableName + "(" + Objects.requireNonNull(exp.getRExpression()).getText() + ")");
                PsiExpression setterExp = factory.createExpressionFromText(className +
                        ".set" + variableName + "(" + exp.getRExpression().getText() + ")", null);
                PsiUtils.log("newExp: " + setterExp.getText());
                // updating setter
                exp.replace(setterExp);

            } else {
                PsiUtils.log("Getter: " + exp);
                // on the right side is just a reference call
                reference.getElement().replace(getterExpression);
            }
        } else if (reference.getElement().getParent() instanceof com.intellij.psi.impl.source.tree.java.PsiBinaryExpressionImpl) {
            PsiUtils.log("Binary: " + reference);
            // on the right side is just a reference call
            reference.getElement().replace(getterExpression);
        } else {
            PsiUtils.log("Unknown !!!!,reference= " + reference + ", Text = (" + reference.getElement().getText() + "), parent = " + reference.getElement().getParent().getClass().getName());

            String refName = reference.toString().substring(reference.toString().lastIndexOf(':') + 1);
            String newExprText = refName.replaceAll(variableName, className + ".get" + variableName + "()");
            PsiUtils.log("newExprText = " + newExprText);
            try {
                PsiExpression newExp = factory.createExpressionFromText(newExprText, null);
                reference.getElement().replace(newExp);
            } catch (IncorrectOperationException e) {
                PsiUtils.log("IncorrectOperationException = " + e);
            } catch (PsiInvalidElementAccessException e) {
                PsiUtils.log("PsiInvalidElementAccessException = " + e);
            }

        }
    }


}
