package fr.adrienbrault.idea.symfony2plugin.dic;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.psi.elements.Method;
import com.jetbrains.php.lang.psi.elements.MethodReference;
import com.jetbrains.php.lang.psi.elements.PhpNamedElement;
import com.jetbrains.php.lang.psi.resolve.types.PhpTypeProvider2;
import fr.adrienbrault.idea.symfony2plugin.Settings;
import fr.adrienbrault.idea.symfony2plugin.Symfony2InterfacesUtil;
import fr.adrienbrault.idea.symfony2plugin.util.PhpElementsUtil;
import fr.adrienbrault.idea.symfony2plugin.util.PhpTypeProviderUtil;
import fr.adrienbrault.idea.symfony2plugin.util.service.ServiceXmlParserFactory;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Adrien Brault <adrien.brault@gmail.com>
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
public class SymfonyContainerTypeProvider implements PhpTypeProvider2 {

    final static char TRIM_KEY = '\u0180';

    @Override
    public char getKey() {
        return '\u0150';
    }

    @Nullable
    @Override
    public String getType(PsiElement e) {

        if (DumbService.getInstance(e.getProject()).isDumb() || !Settings.getInstance(e.getProject()).pluginEnabled || !Settings.getInstance(e.getProject()).symfonyContainerTypeProvider) {
            return null;
        }

        // container calls are only on "get" methods
        if(!(e instanceof MethodReference) || !PhpElementsUtil.isMethodWithFirstStringOrFieldReference(e, "get")) {
            return null;
        }

        return PhpTypeProviderUtil.getReferenceSignature((MethodReference) e, TRIM_KEY);
    }

    @Override
    public Collection<? extends PhpNamedElement> getBySignature(String expression, Project project) {

        // get back our original call
        String originalSignature = expression.substring(0, expression.lastIndexOf(TRIM_KEY));
        String parameter = expression.substring(expression.lastIndexOf(TRIM_KEY) + 1);

        // search for called method
        PhpIndex phpIndex = PhpIndex.getInstance(project);
        Collection<? extends PhpNamedElement> phpNamedElementCollections = phpIndex.getBySignature(originalSignature, null, 0);
        if(phpNamedElementCollections.size() == 0) {
            return Collections.emptySet();
        }

        // get first matched item
        PhpNamedElement phpNamedElement = phpNamedElementCollections.iterator().next();
        if(!(phpNamedElement instanceof Method)) {
            return phpNamedElementCollections;
        }

        parameter = PhpTypeProviderUtil.getResolvedParameter(phpIndex, parameter);
        if(parameter == null) {
            return phpNamedElementCollections;
        }

        // finally search the classes
        if(new Symfony2InterfacesUtil().isContainerGetCall((Method) phpNamedElement)) {
            ServiceMap serviceMap = ServiceXmlParserFactory.getInstance(project, XmlServiceParser.class).getServiceMap();
            String serviceClass = serviceMap.getMap().get(parameter);
            if(serviceClass != null) {
                return PhpIndex.getInstance(project).getAnyByFQN(serviceClass);
            }
        }

        return phpNamedElementCollections;
    }

}
