package com.hz.yk;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.CollectionListModel;

import java.util.List;

/**
 * @author wuzheng.yk
 * @date 2021/5/12
 */
public class GenerateAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent event) {
        //这里的思路是替换整个psiMethod，方法的声明是写死的。可能存在坑点，更好的是利用 PsiCodeBlock
        PsiMethod psiMethod = getPsiMethodFromContext(event);
        generateO2OMethod(psiMethod);
    }

    /**
     * 启动写线程
     *
     * @param psiMethod
     */
    private void generateO2OMethod(final PsiMethod psiMethod) {
        WriteCommandAction.runWriteCommandAction(psiMethod.getProject(), () -> {
            createO2O(psiMethod);
        });
    }

    private void createO2O(PsiMethod psiMethod) {
        String methodName = psiMethod.getName();
        PsiType returnType = psiMethod.getReturnType();
        if (returnType == null) {
            return;
        }
        String returnClassName = returnType.getPresentableText();
        PsiParameter psiParameter = psiMethod.getParameterList().getParameters()[0];
        //带package的class名称
        String parameterClassWithPackage = psiParameter.getType().getCanonicalText(false);
        //为了解析字段，这里需要加载参数的class
        JavaPsiFacade facade = JavaPsiFacade.getInstance(psiMethod.getProject());
        PsiClass paramentClass = facade
                .findClass(parameterClassWithPackage, GlobalSearchScope.allScope(psiMethod.getProject()));
        if (paramentClass == null) {
            return;
        }

        //把原来的getFields 替换成getAllFields ，支持父类的field
        List<PsiField> paramentFields = new CollectionListModel<PsiField>(paramentClass.getAllFields()).getItems();
        String methodText = getMethodText(methodName, returnClassName, psiParameter, paramentFields);
        PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(psiMethod.getProject());
        PsiMethod toMethod = elementFactory.createMethodFromText(methodText, psiMethod);
        psiMethod.replace(toMethod);
    }

    /**
     * 获取方法体
     *
     * @param returnClassName
     * @param psiParameter
     * @param paramentFields
     * @return
     */
    private String getCodeBlock(String returnClassName, PsiParameter psiParameter, List<PsiField> paramentFields) {
        String returnObjName = returnClassName.substring(0, 1).toLowerCase() + returnClassName.substring(1);
        String parameterName = psiParameter.getName();
        StringBuilder builder = new StringBuilder();
        builder.append("if ( " + parameterName + "== null ){\n").append("return null;\n}")
                .append(returnClassName + " " + returnObjName + "= new " + returnClassName + "();\n");
        for (PsiField field : paramentFields) {
            PsiModifierList modifierList = field.getModifierList();
            if (modifierList == null || modifierList.hasModifierProperty(PsiModifier.STATIC) || modifierList
                    .hasModifierProperty(PsiModifier.FINAL) || modifierList
                        .hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
                continue;
            }
            builder.append(returnObjName + ".set" + getFirstUpperCase(field.getName()) + "(" + parameterName + ".get"
                           + getFirstUpperCase(field.getName()) + "());\n");
        }
        builder.append("return " + returnObjName + ";\n");
        return builder.toString();
    }

    /**
     * @param methodName      方法名称
     * @param returnClassName 返回的值的class名称
     * @param psiParameter    方法参数第一个值
     * @param paramentFields  方法参数的class里field 列表
     * @return 方法体的字符串
     */
    private String getMethodText(String methodName, String returnClassName, PsiParameter psiParameter,
                                 List<PsiField> paramentFields) {
        String returnObjName = returnClassName.substring(0, 1).toLowerCase() + returnClassName.substring(1);
        String parameterClass = psiParameter.getText();
        String parameterName = psiParameter.getName();
        StringBuilder builder = new StringBuilder("public static " + returnClassName + " " + methodName + " (");
        builder.append(parameterClass + " ) {\n");
        builder.append("if ( " + parameterName + "== null ){\n").append("return null;\n}")
                .append(returnClassName + " " + returnObjName + "= new " + returnClassName + "();\n");
        for (PsiField field : paramentFields) {
            PsiModifierList modifierList = field.getModifierList();
            if (modifierList == null || modifierList.hasModifierProperty(PsiModifier.STATIC) || modifierList
                    .hasModifierProperty(PsiModifier.FINAL) || modifierList
                        .hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
                continue;
            }
            builder.append(returnObjName + ".set" + getFirstUpperCase(field.getName()) + "(" + parameterName + ".get"
                           + getFirstUpperCase(field.getName()) + "());\n");
        }
        builder.append("return " + returnObjName + ";\n");
        builder.append("}\n");
        return builder.toString();
    }

    private String getFirstUpperCase(String oldStr) {
        return oldStr.substring(0, 1).toUpperCase() + oldStr.substring(1);
    }

    private PsiMethod getPsiMethodFromContext(AnActionEvent e) {
        PsiElement elementAt = getPsiElement(e);
        if (elementAt == null) {
            return null;
        }
        return PsiTreeUtil.getParentOfType(elementAt, PsiMethod.class);
    }

    /**
     * 获取代码块
     *
     * @param e
     * @return
     */
    private PsiCodeBlock getPsiCodeBlock(AnActionEvent e) {
        final PsiMethod psiMethod = getPsiMethodFromContext(e);
        if (psiMethod == null) {
            return null;
        } else {
            return PsiTreeUtil.getChildOfType(psiMethod, PsiCodeBlock.class);
        }
    }

    private PsiElement getPsiElement(AnActionEvent e) {
        PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);
        Editor editor = e.getData(PlatformDataKeys.EDITOR);
        if (psiFile == null || editor == null) {
            e.getPresentation().setEnabled(false);
            return null;
        }
        //用来获取当前光标处的PsiElement
        int offset = editor.getCaretModel().getOffset();
        return psiFile.findElementAt(offset);
    }
}
