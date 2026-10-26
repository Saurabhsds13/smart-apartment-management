package com.societix.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ArchUnit module-boundary tests for the Societix modular monolith.
 *
 * <p>The application is partitioned into bounded modules under {@code com.societix.<module>}.
 * Each module exposes a public {@code api} sub-package (service interfaces + DTOs) and keeps its
 * entities/repositories/controllers in an {@code internal} sub-package. These tests enforce the
 * three boundary rules from the design ("Module Architecture", Req A1):
 *
 * <ol>
 *   <li>No cross-module access to {@code *.internal} packages — only a module may touch its own
 *       internal package.</li>
 *   <li>No cyclic dependencies between the top-level module packages.</li>
 *   <li>Controllers and JPA entities never cross module boundaries — a controller in one module
 *       does not reference another module's controllers or entities directly.</li>
 * </ol>
 *
 * <p>Because most modules are currently empty skeletons, these rules are written so they hold on
 * the current codebase (rules evaluated over empty package slices find no classes and therefore no
 * violations) while still catching violations introduced by future code.
 */
@DisplayName("Module boundary enforcement (ArchUnit)")
class ModuleBoundaryTest {

    /** Root package of the application. */
    private static final String ROOT = "com.societix";

    /** Fully-qualified annotation names, referenced by string so no web/jpa test imports are needed. */
    private static final String REST_CONTROLLER = "org.springframework.web.bind.annotation.RestController";
    private static final String CONTROLLER = "org.springframework.stereotype.Controller";
    private static final String ENTITY = "jakarta.persistence.Entity";

    /**
     * The 17 bounded modules (design "Module Responsibilities"). Kept as a constant so the
     * per-module rules cover every module explicitly.
     */
    private static final List<String> MODULES = List.of(
            "tenancy",
            "identity",
            "authorization",
            "audit",
            "notification",
            "filestorage",
            "societyproperty",
            "resident",
            "communication",
            "maintenance",
            "billing",
            "payments",
            "visitor",
            "facilitybooking",
            "platformadmin",
            "reporting",
            "common");

    /**
     * Production classes only. Test classes (this package included) are excluded so the boundary
     * rules describe the shipped application, not its test harness.
     */
    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .withImportOption(new ImportOption.DoNotIncludeJars())
            .importPackages(ROOT);

    // ---------------------------------------------------------------------
    // (1) No cross-module access to *.internal packages.
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("a module's *.internal package is accessed only from within that same module")
    void internalPackagesAreNotAccessedAcrossModules() {
        for (String module : MODULES) {
            String internalPackages = ROOT + "." + module + ".internal..";
            String ownModulePackages = ROOT + "." + module + "..";

            ArchRule rule = classes()
                    .that().resideInAPackage(internalPackages)
                    .should().onlyBeAccessed().byAnyPackage(ownModulePackages)
                    .as("classes in %s may only be accessed from within the %s module"
                            .formatted(internalPackages, module))
                    // Empty slices (skeleton modules) have no classes to check — do not fail.
                    .allowEmptyShould(true);

            rule.check(PRODUCTION_CLASSES);
        }
    }

    // ---------------------------------------------------------------------
    // (2) No cycles between the top-level module packages.
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("there are no cyclic dependencies between modules")
    void modulesAreFreeOfCycles() {
        ArchRule rule = slices()
                .matching(ROOT + ".(*)..")
                .should().beFreeOfCycles()
                .allowEmptyShould(true);

        rule.check(PRODUCTION_CLASSES);
    }

    // ---------------------------------------------------------------------
    // (3) Controllers and entities never cross module boundaries.
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("a controller does not reference another module's controllers")
    void controllersDoNotReferenceOtherModulesControllers() {
        for (String module : MODULES) {
            ArchRule rule = noClasses()
                    .that(isControllerInModule(module))
                    .should().dependOnClassesThat(isControllerInOtherModule(module))
                    .as("controllers in the %s module must not depend on another module's controllers"
                            .formatted(module))
                    // No controllers yet in skeleton modules — rule must still pass.
                    .allowEmptyShould(true);

            rule.check(PRODUCTION_CLASSES);
        }
    }

    @Test
    @DisplayName("a controller does not reference another module's entities")
    void controllersDoNotReferenceOtherModulesEntities() {
        for (String module : MODULES) {
            ArchRule rule = noClasses()
                    .that(isControllerInModule(module))
                    .should().dependOnClassesThat(isEntityInOtherModule(module))
                    .as("controllers in the %s module must not depend on another module's entities"
                            .formatted(module))
                    .allowEmptyShould(true);

            rule.check(PRODUCTION_CLASSES);
        }
    }

    @Test
    @DisplayName("entities are not accessed by classes outside their own module")
    void entitiesDoNotCrossModuleBoundaries() {
        for (String module : MODULES) {
            String ownModulePackages = ROOT + "." + module + "..";

            ArchRule rule = classes()
                    .that(isEntityInModule(module))
                    .should().onlyBeAccessed().byAnyPackage(ownModulePackages)
                    .as("JPA entities of the %s module must not be accessed from other modules"
                            .formatted(module))
                    // No entities yet in skeleton modules — rule must still pass.
                    .allowEmptyShould(true);

            rule.check(PRODUCTION_CLASSES);
        }
    }

    // ---------------------------------------------------------------------
    // Predicates
    // ---------------------------------------------------------------------

    private static boolean inModule(JavaClass clazz, String module) {
        String prefix = ROOT + "." + module;
        String pkg = clazz.getPackageName();
        return pkg.equals(prefix) || pkg.startsWith(prefix + ".");
    }

    private static boolean isController(JavaClass clazz) {
        return clazz.isAnnotatedWith(REST_CONTROLLER) || clazz.isAnnotatedWith(CONTROLLER);
    }

    private static boolean isEntity(JavaClass clazz) {
        return clazz.isAnnotatedWith(ENTITY);
    }

    private static DescribedPredicate<JavaClass> isControllerInModule(String module) {
        return DescribedPredicate.describe(
                "controllers in the %s module".formatted(module),
                clazz -> inModule(clazz, module) && isController(clazz));
    }

    private static DescribedPredicate<JavaClass> isControllerInOtherModule(String module) {
        return DescribedPredicate.describe(
                "controllers in a module other than %s".formatted(module),
                clazz -> !inModule(clazz, module) && isController(clazz));
    }

    private static DescribedPredicate<JavaClass> isEntityInModule(String module) {
        return DescribedPredicate.describe(
                "JPA entities in the %s module".formatted(module),
                clazz -> inModule(clazz, module) && isEntity(clazz));
    }

    private static DescribedPredicate<JavaClass> isEntityInOtherModule(String module) {
        return DescribedPredicate.describe(
                "JPA entities in a module other than %s".formatted(module),
                clazz -> !inModule(clazz, module) && isEntity(clazz));
    }
}
