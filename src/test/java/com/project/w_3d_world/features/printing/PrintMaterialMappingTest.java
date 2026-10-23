package com.project.w_3d_world.features.printing;

import com.project.w_3d_world.features.printing.entity.PrintMaterial;
import com.project.w_3d_world.features.printing.repository.PrintMaterialRepository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class PrintMaterialMappingTest {
    @Test
    void entityMapsTheVerifiedSixColumnTable() throws Exception {
        assertTrue(PrintMaterial.class.isAnnotationPresent(Entity.class));
        assertEquals("print_materials", PrintMaterial.class.getAnnotation(Table.class).name());

        Map<String, String> expectedColumns = Map.of(
                "materialId", "material_id",
                "materialName", "material_name",
                "technologyCode", "technology_code",
                "unit", "unit",
                "estimatedUnitCost", "estimated_unit_cost",
                "isActive", "is_active");
        Set<String> expectedFields = expectedColumns.keySet();
        Set<String> mappedFields = Arrays.stream(PrintMaterial.class.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(Column.class))
                .map(Field::getName)
                .collect(Collectors.toSet());
        assertEquals(expectedFields, mappedFields);

        for (var entry : expectedColumns.entrySet()) {
            assertEquals(entry.getValue(), PrintMaterial.class.getDeclaredField(entry.getKey())
                    .getAnnotation(Column.class).name());
        }

        Field id = PrintMaterial.class.getDeclaredField("materialId");
        assertEquals(Integer.class, id.getType());
        assertTrue(id.isAnnotationPresent(Id.class));
        assertEquals(GenerationType.IDENTITY,
                id.getAnnotation(GeneratedValue.class).strategy());

        Field name = PrintMaterial.class.getDeclaredField("materialName");
        Field technology = PrintMaterial.class.getDeclaredField("technologyCode");
        Field unit = PrintMaterial.class.getDeclaredField("unit");
        Field cost = PrintMaterial.class.getDeclaredField("estimatedUnitCost");
        Field active = PrintMaterial.class.getDeclaredField("isActive");

        assertEquals(String.class, name.getType());
        assertEquals(255, name.getAnnotation(Column.class).length());
        assertFalse(name.getAnnotation(Column.class).nullable());
        assertEquals(String.class, technology.getType());
        assertEquals(30, technology.getAnnotation(Column.class).length());
        assertFalse(technology.getAnnotation(Column.class).nullable());
        assertEquals(String.class, unit.getType());
        assertEquals(30, unit.getAnnotation(Column.class).length());
        assertTrue(unit.getAnnotation(Column.class).nullable());
        assertEquals(BigDecimal.class, cost.getType());
        assertEquals(18, cost.getAnnotation(Column.class).precision());
        assertEquals(2, cost.getAnnotation(Column.class).scale());
        assertTrue(cost.getAnnotation(Column.class).nullable());
        assertEquals(Boolean.class, active.getType());
        assertFalse(active.getAnnotation(Column.class).nullable());

        var material = new PrintMaterial("PLA", "FDM", null, null);
        assertTrue(material.getIsActive());
        assertNull(material.getUnit());
        assertNull(material.getEstimatedUnitCost());
    }

    @Test
    void repositoryExtendsJpaRepositoryAndDeclaresOnlyCatalogQueries() {
        var repository = PrintMaterialRepository.class.getGenericInterfaces()[0];
        assertTrue(repository instanceof ParameterizedType);
        var parameters = ((ParameterizedType) repository).getActualTypeArguments();
        assertEquals(PrintMaterial.class, parameters[0]);
        assertEquals(Integer.class, parameters[1]);

        Method active = method("findByIsActiveTrue");
        Method byTechnology = method("findByIsActiveTrueAndTechnologyCode");
        assertEquals(Page.class, active.getReturnType());
        assertArrayEquals(new Class<?>[] {Pageable.class}, active.getParameterTypes());
        assertEquals(Page.class, byTechnology.getReturnType());
        assertArrayEquals(new Class<?>[] {String.class, Pageable.class}, byTechnology.getParameterTypes());
    }

    private static Method method(String name) {
        return Arrays.stream(PrintMaterialRepository.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
