package halo.query.mapping;

import halo.query.javassistutil.JavassistUtil;
import javassist.*;

import java.lang.reflect.Field;

public class JavassitSQLMapperClassCreater<T> {

    private final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

    private Class<T> mapperClass;

    public JavassitSQLMapperClassCreater(EntityTableInfo<T> entityTableInfo) {
        super();
        String mapperClassName = this.createMapperClassName(entityTableInfo.getClazz());
        try {
            ClassPool pool = JavassistUtil.getClassPool();
            CtClass sqlMapperClass = pool.get(SQLMapper.class.getName());
            CtClass cc;
            try {
                pool.getCtClass(mapperClassName);
                this.mapperClass = (Class<T>) classLoader.loadClass(mapperClassName);
            } catch (NotFoundException e) {
                cc = pool.makeClass(mapperClassName);
                cc.setInterfaces(new CtClass[]{sqlMapperClass});
                this.createGetIdParamMethod(entityTableInfo, cc);
                this.createGetParamsForInsertMethod(entityTableInfo, cc);
                this.createGetParamsForUpdateMethod(entityTableInfo, cc);
                this.mapperClass = cc.toClass(classLoader, classLoader.getClass().getProtectionDomain());
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        } catch (CannotCompileException e) {
            throw new RuntimeException(e);
        } catch (NotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public Class<T> getMapperClass() {
        return mapperClass;
    }

    private void createGetIdParamMethod(EntityTableInfo<T> entityTableInfo, CtClass cc) {
        StringBuilder sb = new StringBuilder("public Object[] getIdParams(Object t){");
        try {
            String className = entityTableInfo.getClazz().getName();
            sb.append(className + " o =(" + className + ")t;\n");
            String paramListUtilClassName = ParamListUtil.class.getName();
            if (entityTableInfo.getIdFields().isEmpty()) {
                sb.append("return null;}");
            } else {
                // return
                sb.append("return new Object[]{\n");
                for (Field idField : entityTableInfo.getIdFields()) {
                    sb.append(paramListUtilClassName).append(".toObject(o.").append
                            (MethodNameUtil.createGetMethodString(idField)
                                    + "())").append(",");
                }
                sb.deleteCharAt(sb.length() - 1);
                sb.append("};}");
            }
            String src = sb.toString();
            CtMethod mapRowMethod = CtNewMethod.make(src, cc);
            cc.addMethod(mapRowMethod);
        } catch (CannotCompileException e) {
            throw new RuntimeException(sb.toString(), e);
        }
    }

    private void createGetParamsForInsertMethod(EntityTableInfo<T> entityTableInfo, CtClass cc) throws CannotCompileException {
        StringBuilder sb = new StringBuilder("public Object[] getParamsForInsert(Object t,boolean hasIdFieldValue){");
        try {
            String className = entityTableInfo.getClazz().getName();
            sb.append(className + " o =(" + className + ")t;");
            // return
            String paramListUtilClassName = ParamListUtil.class.getName();
            if (entityTableInfo.getIdFields().size() > 1) {
                sb.append("\n\t return new Object[]{");
                for (Field field : entityTableInfo.getTableFields()) {
                    sb.append(paramListUtilClassName + ".toObject(o."
                            + MethodNameUtil.createGetMethodString(field)
                            + "()),");
                }
                if (sb.charAt(sb.length() - 1) == ',') {
                    sb.deleteCharAt(sb.length() - 1);
                }
                sb.append("};");
            } else {
                sb.append("if(hasIdFieldValue)");
                // return code
                sb.append("\n\t return new Object[]{");
                for (Field field : entityTableInfo.getTableFields()) {
                    sb.append(paramListUtilClassName + ".toObject(o."
                            + MethodNameUtil.createGetMethodString(field)
                            + "()),");
                }
                if (sb.charAt(sb.length() - 1) == ',') {
                    sb.deleteCharAt(sb.length() - 1);
                }
                sb.append("};");
                // return code end
                // return code
                sb.append("\n return new Object[]{");
                for (Field field : entityTableInfo.getTableFields()) {
                    if (entityTableInfo.isIdField(field)) {
                        continue;
                    }
                    sb.append(paramListUtilClassName + ".toObject(o."
                            + MethodNameUtil.createGetMethodString(field) + "()),");
                }
                if (sb.charAt(sb.length() - 1) == ',') {
                    sb.deleteCharAt(sb.length() - 1);
                }
                sb.append("};");
                // return code end
            }
            sb.append("}");
            String src = sb.toString();
            CtMethod mapRowMethod = CtNewMethod.make(src, cc);
            cc.addMethod(mapRowMethod);
        } catch (CannotCompileException e) {
            throw new RuntimeException(sb.toString(), e);
        }
    }

    private void createGetParamsForUpdateMethod(
            EntityTableInfo<T> entityTableInfo,
            CtClass cc) throws CannotCompileException {
        String className = entityTableInfo.getClazz().getName();
        StringBuilder sb = new StringBuilder(
                "public Object[] getParamsForUpdate(Object t){");
        sb.append(className + " o =(" + className + ")t;");
        // return
        String paramListUtilClassName = ParamListUtil.class.getName();
        sb.append("return new Object[]{");
        for (Field field : entityTableInfo.getTableFields()) {
            if (!entityTableInfo.isIdField(field)) {
                sb.append(paramListUtilClassName + ".toObject(o."
                        + MethodNameUtil.createGetMethodString(field)
                        + "()),");
            }
        }
        for (Field field : entityTableInfo.getIdFields()) {
            sb.append(paramListUtilClassName + ".toObject(o." +
                    MethodNameUtil.createGetMethodString(field) + "()),");
        }
        sb.deleteCharAt(sb.length() - 1);
        sb.append("};");
        sb.append("}");
        String src = sb.toString();
        CtMethod mapRowMethod = CtNewMethod.make(src, cc);
        cc.addMethod(mapRowMethod);
    }

    private String createMapperClassName(Class<?> clazz) {
        int idx = clazz.getName().lastIndexOf(".");
        String shortName = clazz.getName().substring(idx + 1);
        String pkgName = clazz.getName().substring(0, idx);
        return pkgName + "." + shortName + "HaloJavassist$SQLMapper";
    }
}
