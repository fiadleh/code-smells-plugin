package com.github.fiadleh.codesmellsplugin.util;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.*;

/**
 * The cache manager contains meta-information about the whole project. The information is created when the project
 * opens and is quickly accessible afterward
 *
 * @author Firas Adleh
 */
public class CacheManager {
    /**
     * A list of classes names with their PSI representation
     */
    private static final HashMap<String, PsiClass> searchedClasses = new HashMap<>();

    public static List<String> getAllClassesQualifiedNames() {
        return allClassesQualifiedNames;
    }

    /**
     * A list of classes names with their full path
     */
    private static List<String> allClassesQualifiedNames = new ArrayList<>();

    /**
     * A list of basic class types that could be ignored when searching for a common hierarchy
     */
    private static final List<String> basicClassNames = Arrays.asList("Object", "Observable", "Cloneable", "Serializable");

    /**
     * A list of all classes names
     */
    private static List<PsiClass> allClasses = new ArrayList<>();

    /**
     * A list of classes names with their hierarchy
     */
    private static HashMap<String, List<String>> allSuperClasses = new HashMap<>();

    /**
     * A flag for creating cache
     */
    private static boolean isCreatingCache = false;

    /**
     * An identifier for cache manger messages in log
     */
    public static final String LOGGER_NAME = CacheManager.class.getSimpleName();

    private CacheManager() {
    }

    /**
     * Returns a list of all classes names or start creating this list if it does not exist
     *
     * @param currentProject
     * @return
     */
    public static List<PsiClass> getAllClasses(Project currentProject) {
        if (allClasses.isEmpty()) {
            createClassesListCache(currentProject);
        }
        return allClasses;
    }

    /**
     * Start creating a list of all classes names
     *
     * @param currentProject
     */
    public static void createClassesListCache(Project currentProject) {
        long startTime = System.currentTimeMillis();
        allClasses = new ArrayList<>();
        Collection<VirtualFile> virtualFiles = com.intellij.psi.search.FileTypeIndex.getFiles(JavaFileType.INSTANCE,
                GlobalSearchScope.projectScope(currentProject));

        for (VirtualFile virtualFile : virtualFiles) {
            PsiFile currentFile = PsiManager.getInstance(currentProject).findFile(virtualFile);

            Collection<PsiClass> classesInFile = PsiTreeUtil.findChildrenOfType(currentFile, PsiClass.class);
            for (PsiClass c : classesInFile) {
                if (c.getQualifiedName() != null && !allClassesQualifiedNames.contains(c.getQualifiedName())) {

                    allClassesQualifiedNames.add(c.getQualifiedName());
                    allClasses.add(c);
                }
            }
        }
        PsiUtils.log(LOGGER_NAME, allClasses.size() + ", createClassesListCache, time=" + (System.currentTimeMillis() - startTime));
    }

    /**
     * Remove a class from classes lists
     *
     * @param theClass
     */
    public static void removeClassFromCache(PsiClass theClass) {
        allClassesQualifiedNames.remove(theClass.getQualifiedName());
        allClasses.remove(theClass);
    }

    /**
     * Add a new class to classes lists
     * @param theClass
     */
    public static void addClassToCache(PsiClass theClass) {
        if (!allClasses.contains(theClass)) {
            PsiUtils.log(LOGGER_NAME, " + + + addClassToCache , time=" + theClass.getQualifiedName());
            allClasses.add(theClass);
            allClassesQualifiedNames.add(theClass.getQualifiedName());
            allSuperClasses.put(theClass.getQualifiedName(), getAllSupperClassesAsString(theClass));
        }
    }

    /**
     * Get a list of all supper classes and inferaces of a given class
     *
     * @param currentClass
     * @return
     */
    public static List<String> getSuperClasses(PsiClass currentClass) {
        if (currentClass.getQualifiedName() == null) {
            return new ArrayList<>();
        }

        // if this class is not the list find his super classes and add them
        if (!allSuperClasses.containsKey(currentClass.getQualifiedName())) {
            allSuperClasses.put(currentClass.getQualifiedName(), getAllSupperClassesAsString(currentClass));
            PsiUtils.log(LOGGER_NAME, "getSuperClasses add one super class , allSuperClasses = " + allSuperClasses.size() + "/" + allClasses.size() + ", currentClass = " + currentClass.getQualifiedName());
        }
        return allSuperClasses.get(currentClass.getQualifiedName());
    }

    /**
     * Start creating a full hierarchy list
     */
    public static void createHierarchyCache() {
        if (isCreatingCache) {
            return;
        }
        isCreatingCache = true;

        long startTime = System.currentTimeMillis();
        PsiUtils.log(LOGGER_NAME, "****  start create Hierarchy Cache  ****");

        if (allClasses.isEmpty()) {
            return;
        }
        for (PsiClass c : allClasses) {
            if (c != null && c.getQualifiedName() != null && !allSuperClasses.containsKey(c.getQualifiedName())) {
                allSuperClasses.put(c.getQualifiedName(), getAllSupperClassesAsString(c));
            }
            if (allSuperClasses.size() % 10 == 0) {
                PsiUtils.log(LOGGER_NAME, "hierarchy  ------>  " + allSuperClasses.size() + "/" + allClasses.size() + " , time=" + (System.currentTimeMillis() - startTime) + " ");

            }
        }
        PsiUtils.log(LOGGER_NAME, "******** hierarchy Cache finished  : classes:" + allClasses.size() + ", time=" + (System.currentTimeMillis() - startTime) + "  ***************");

    }

    /**
     * Returns a list of all supper classes for a given class as a PsiClass list
     *
     * @param currentClass
     * @return
     */
    public static Deque<PsiClass> getAllSupperClasses(PsiClass currentClass) {
        Deque<PsiClass> supersList = new ArrayDeque<>();
        ArrayList<String> doneList = new ArrayList<>();
        Deque<PsiClass> resultsList = new ArrayDeque<>();

        supersList.add(currentClass);

        while (!supersList.isEmpty()) {
            PsiClass currentSupper = supersList.pop();
            if (currentSupper.getName() != null && !basicClassNames.contains(currentSupper.getName())
            ) {
                resultsList.add(currentSupper);
                for (PsiClass currentSupClass : currentSupper.getSupers()) {
                    if (!basicClassNames.contains(currentSupClass.getName())) {

                        supersList.add(currentSupClass);
                        doneList.add(currentSupClass.getName());
                    }
                }

                addSuperTypes(currentClass, supersList, doneList, currentSupper);

            }

        }
        return resultsList;
    }

    /**
     * Adds direct supper classes and interfaces of one class to its full suppers list
     *
     * @param currentClass
     * @param supersList
     * @param doneList
     * @param currentSupper
     */
    private static void addSuperTypes(PsiClass currentClass, Deque<PsiClass> supersList, ArrayList<String> doneList, PsiClass currentSupper) {
        for (PsiClassType superType : currentSupper.getSuperTypes()) {
            if (!doneList.contains(superType.getName()) && searchedClasses.get(superType.getName()) == null) {
                for (PsiClass cl : PsiUtils.getClassFromType(currentClass.getProject(), superType.getName())) {
                    if (!supersList.contains(cl) && !basicClassNames.contains(cl.getName())) {
                        supersList.add(cl);
                        doneList.add(cl.getName());
                        searchedClasses.put(superType.getName(), cl);
                    }
                }
            }

            if (!doneList.contains(superType.getName()) && searchedClasses.get(superType.getName()) != null) {
                supersList.add(searchedClasses.get(superType.getName()));
                doneList.add(searchedClasses.get(superType.getName()).getName());
            }
        }
    }

    /**
     * Returns a list of all supper classes for a given class as a string list
     *
     * @param currentClass
     * @return
     */
    public static List<String> getAllSupperClassesAsString(PsiClass currentClass) {
        List<String> results = new ArrayList<>();
        for (PsiClass cl : getAllSupperClasses(currentClass)) {
            results.add(cl.getName());
        }

        return results;
    }

    /***
     * reset the cache vars to start making the cache from scratch
     */
    public static void resetIsCacheReady() {
        allClasses = new ArrayList<>();
        allSuperClasses = new HashMap<>();
        allClassesQualifiedNames = new ArrayList<>();
        isCreatingCache = false;
        PsiUtils.log(LOGGER_NAME, "     $$$$$    reset Cache    $$$$$        allSuperClasses:" + allSuperClasses.size());

    }
}
