package com.example.api.architecture;
import com.tngtech.archunit.junit.*;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
@AnalyzeClasses(packages="com.example.api", importOptions=com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
    @ArchTest static final ArchRule controllersUsePorts = noClasses().that().resideInAPackage("..adapter.in..")
        .should().dependOnClassesThat().resideInAnyPackage("..application.usecase..", "..adapter.out..");
    @ArchTest static final ArchRule coreIsPure = noClasses().that().resideInAnyPackage("..domain..", "..application..")
        .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..", "jakarta..", "io.quarkus..", "com.google..", "com.corundumstudio..");
}
