package de.cofinpro.account.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.function.Supplier;

public class JPAUnitTestValidator<T> {

    private final Validator validator;
    private final Supplier<? extends T> getValidFunction;

    public JPAUnitTestValidator(Supplier<? extends T> getValidObjectFunction) {
        try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
            this.validator = validatorFactory.getValidator();
        }
        this.getValidFunction = getValidObjectFunction;
    }

    public Set<ConstraintViolation<T>> getConstraintViolationsOnUpdate(String fieldToUpdate, Object newValue)
            throws Exception {
        T object = getValidFunction.get();
        updateFieldByReflection(object, fieldToUpdate, newValue);

        return validator.validate(object);
    }

    private void updateFieldByReflection(Object object, String fieldName, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(object, value);
    }
}
