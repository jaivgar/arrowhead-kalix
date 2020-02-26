package eu.arrowhead.kalix.dto;

import eu.arrowhead.kalix.dto.types.*;
import eu.arrowhead.kalix.dto.types.DTOInterface;

import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DTOPropertyFactory {
    private final Types typeUtils;

    private final DeclaredType booleanType;
    private final DeclaredType byteType;
    private final DeclaredType characterType;
    private final DeclaredType doubleType;
    private final DeclaredType floatType;
    private final DeclaredType integerType;
    private final DeclaredType listType;
    private final DeclaredType longType;
    private final DeclaredType mapType;
    private final DeclaredType optionalType;
    private final DeclaredType shortType;
    private final DeclaredType stringType;

    private final DeclaredType readableDTOType;
    private final DeclaredType writableDTOType;

    private final Set<Modifier> publicStaticModifiers;

    public DTOPropertyFactory(final Elements elementUtils, final Types typeUtils) {
        this.typeUtils = typeUtils;

        booleanType = getDeclaredTypeOf(elementUtils, Boolean.class);
        byteType = getDeclaredTypeOf(elementUtils, Byte.class);
        characterType = getDeclaredTypeOf(elementUtils, Character.class);
        doubleType = getDeclaredTypeOf(elementUtils, Double.class);
        floatType = getDeclaredTypeOf(elementUtils, Float.class);
        integerType = getDeclaredTypeOf(elementUtils, Integer.class);
        listType = getDeclaredTypeOf(elementUtils, List.class);
        longType = getDeclaredTypeOf(elementUtils, Long.class);
        mapType = getDeclaredTypeOf(elementUtils, Map.class);
        optionalType = getDeclaredTypeOf(elementUtils, Optional.class);
        shortType = getDeclaredTypeOf(elementUtils, Short.class);
        stringType = getDeclaredTypeOf(elementUtils, String.class);

        readableDTOType = getDeclaredTypeOf(elementUtils, ReadableDTO.class);
        writableDTOType = getDeclaredTypeOf(elementUtils, WritableDTO.class);

        publicStaticModifiers = Stream.of(Modifier.PUBLIC, Modifier.STATIC)
            .collect(Collectors.toSet());
    }

    private static DeclaredType getDeclaredTypeOf(final Elements elementUtils, final Class<?> class_) {
        return (DeclaredType) elementUtils.getTypeElement(class_.getCanonicalName()).asType();
    }

    public DTOProperty createFromMethod(final ExecutableElement method) throws DTOException {
        assert method.getKind() == ElementKind.METHOD;
        assert method.getEnclosingElement().getKind() == ElementKind.INTERFACE;
        assert !method.getModifiers().contains(Modifier.DEFAULT);
        assert !method.getModifiers().contains(Modifier.STATIC);

        if (method.getReturnType().getKind() == TypeKind.VOID ||
            method.getParameters().size() != 0 ||
            method.getTypeParameters().size() != 0
        ) {
            throw new DTOException(method, "@Readable/@Writable interface " +
                "methods must either be static, provide a default " +
                "implementation, or be simple getters, which means that " +
                "they have a non-void return type, takes no arguments and " +
                "does not require any type parameters");
        }

        final var builder = new DTOProperty.Builder()
            .name(method.getSimpleName().toString())
            .formatNames(collectFormatNamesFrom(method));

        var type = method.getReturnType();

        if (type.getKind().isPrimitive()) {
            return builder
                .type(toPrimitiveType(type))
                .isOptional(false)
                .build();
        }
        if (type.getKind() == TypeKind.ARRAY) {
            return builder
                .type(toArrayType(method, type))
                .isOptional(false)
                .build();
        }

        if (typeUtils.isAssignable(typeUtils.erasure(type), optionalType)) {
            final var declaredType = (DeclaredType) type;
            final var argumentType = declaredType.getTypeArguments().get(0);
            return builder
                .type(resolveType(method, argumentType))
                .isOptional(true)
                .build();
        }

        return builder
            .type(resolveType(method, type))
            .isOptional(false)
            .build();
    }

    private Map<Format, String> collectFormatNamesFrom(final Element method) {
        final var formatNames = new HashMap<Format, String>();
        final var nameJSON = method.getAnnotation(NameJSON.class);
        if (nameJSON != null) {
            formatNames.put(Format.JSON, nameJSON.value());
        }
        return formatNames;
    }

    private DTOType resolveType(final ExecutableElement method, final TypeMirror type) throws DTOException {
        if (type.getKind().isPrimitive()) {
            return toPrimitiveType(type);
        }
        if (type.getKind() == TypeKind.ARRAY) {
            return toArrayType(method, type);
        }
        if (typeUtils.isSameType(booleanType, type) ||
            typeUtils.isSameType(byteType, type) ||
            typeUtils.isSameType(characterType, type) ||
            typeUtils.isSameType(doubleType, type) ||
            typeUtils.isSameType(floatType, type) ||
            typeUtils.isSameType(integerType, type) ||
            typeUtils.isSameType(longType, type) ||
            typeUtils.isSameType(shortType, type)
        ) {
            return toPrimitiveBoxedType(type);
        }
        if (typeUtils.asElement(type).getKind() == ElementKind.ENUM) {
            return toEnumType(type);
        }
        if (typeUtils.isAssignable(typeUtils.erasure(type), listType)) {
            return toListType(method, type);
        }
        if (typeUtils.isAssignable(typeUtils.erasure(type), mapType)) {
            return toMapType(method, type);
        }
        if (typeUtils.isSameType(stringType, type)) {
            return toStringType(type);
        }
        if (isEnumLike(type)) {
            return toEnumLikeType(type);
        }
        return toInterfaceType(method, type);
    }

    private boolean isEnumLike(final TypeMirror type) {
        final var element = typeUtils.asElement(type);
        if (element == null || element.getKind() != ElementKind.CLASS) {
            return false;
        }

        var hasValueOf = false;
        var hasToString = false;

        final var typeElement = (TypeElement) element;
        for (final var enclosed : typeElement.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.METHOD) {
                continue;
            }
            final var executable = (ExecutableElement) enclosed;
            final var name = executable.getSimpleName().toString();
            if (!hasValueOf && Objects.equals(name, "valueOf")) {
                if (!executable.getModifiers().containsAll(publicStaticModifiers)) {
                    continue;
                }
                final var parameters = executable.getParameters();
                if (parameters.size() != 1 || !typeUtils.isSameType(parameters.get(0).asType(), stringType)) {
                    continue;
                }
                hasValueOf = true;
            }
            if (!hasToString && Objects.equals(name, "toString")) {
                final var modifiers = executable.getModifiers();
                if (modifiers.size() != 1 || !modifiers.contains(Modifier.PUBLIC)) {
                    continue;
                }
                final var parameters = executable.getParameters();
                if (parameters.size() != 0) {
                    continue;
                }
                hasToString = true;
            }
        }
        return hasValueOf && hasToString;
    }

    private DTOArray toArrayType(final ExecutableElement method, final TypeMirror type) throws DTOException {
        final var arrayType = (ArrayType) type;
        final var element = resolveType(method, arrayType.getComponentType());
        return new DTOArray(arrayType, element);
    }

    private DTOInterface toInterfaceType(final ExecutableElement method, final TypeMirror type) throws DTOException {
        final var declaredType = (DeclaredType) type;
        final var element = declaredType.asElement();

        final var readable = element.getAnnotation(Readable.class);
        final var writable = element.getAnnotation(Writable.class);

        if (readable == null && writable == null) {
            if (typeUtils.isAssignable(type, readableDTOType) ||
                typeUtils.isAssignable(type, writableDTOType)
            ) {
                throw new DTOException(method, "Generated DTO classes may " +
                    "not be used in interfaces annotated with @Readable or " +
                    "@Writable; rather use the interface types from which " +
                    "those DTO classes were generated");
            }
            throw new DTOException(method, "Getter return type must be a " +
                "primitive, a boxed primitive, a String, an array (T[]), " +
                "a List<T>, a Map<K, V>, an enum, an enum-like class, which " +
                "overrides toString() and has a public static " +
                "valueOf(String) method, or be annotated with @Readable " +
                "and/or @Writable; if an array, list or map, their " +
                "parameters must conform to the same requirements");
        }

        final var readableFormats = readable != null ? readable.value() : new Format[0];
        final var writableFormats = writable != null ? writable.value() : new Format[0];

        return new DTOInterface(declaredType, readableFormats, writableFormats);
    }

    private DTOEnum toEnumType(final TypeMirror type) {
        return new DTOEnum((DeclaredType) type);
    }

    private DTOEnumLike toEnumLikeType(final TypeMirror type) {
        return new DTOEnumLike((DeclaredType) type);
    }

    private DTOList toListType(final ExecutableElement method, final TypeMirror type) throws DTOException {
        final var declaredType = (DeclaredType) type;
        final var element = resolveType(method, declaredType.getTypeArguments().get(0));
        return new DTOList(declaredType, element);
    }

    private DTOMap toMapType(final ExecutableElement method, final TypeMirror type) throws DTOException {
        final var declaredType = (DeclaredType) type;
        final var typeArguments = declaredType.getTypeArguments();
        final var key = resolveType(method, typeArguments.get(0));
        if (key.isCollection() || key instanceof DTOInterface) {
            throw new DTOException(method, "Only boxed primitives, enums, " +
                "enum-likes and strings may be used as Map keys");
        }
        final var value = resolveType(method, typeArguments.get(1));
        return new DTOMap(declaredType, key, value);
    }

    private DTOPrimitive toPrimitiveType(final TypeMirror type) {
        return new DTOPrimitive((PrimitiveType) type);
    }

    private DTOPrimitiveBoxed toPrimitiveBoxedType(final TypeMirror type) {
        return new DTOPrimitiveBoxed((DeclaredType) type);
    }

    private DTOString toStringType(final TypeMirror type) {
        return new DTOString((DeclaredType) type);
    }
}