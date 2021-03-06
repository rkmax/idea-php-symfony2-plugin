package fr.adrienbrault.idea.symfony2plugin.navigation;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import fr.adrienbrault.idea.symfony2plugin.Symfony2ProjectComponent;
import fr.adrienbrault.idea.symfony2plugin.config.SymfonyPhpReferenceContributor;
import fr.adrienbrault.idea.symfony2plugin.routing.PhpRouteReferenceContributor;
import fr.adrienbrault.idea.symfony2plugin.routing.Route;
import fr.adrienbrault.idea.symfony2plugin.routing.RouteHelper;
import fr.adrienbrault.idea.symfony2plugin.util.MethodMatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;


public class PhpFoldingBuilder extends FoldingBuilderEx {

    @NotNull
    @Override
    public FoldingDescriptor[] buildFoldRegions(@NotNull PsiElement psiElement, @NotNull Document document, boolean b) {

        if (!Symfony2ProjectComponent.isEnabled(psiElement)) {
            return new FoldingDescriptor[0];
        }

        List<FoldingDescriptor> descriptors = new ArrayList<FoldingDescriptor>();
        FoldingGroup model = FoldingGroup.newGroup("model");
        FoldingGroup template = FoldingGroup.newGroup("template");

        Collection<StringLiteralExpression> stringLiteralExpressiones = PsiTreeUtil.findChildrenOfType(psiElement, StringLiteralExpression.class);
        for(StringLiteralExpression stringLiteralExpression: stringLiteralExpressiones) {
            attachModelShortcuts(descriptors, model, stringLiteralExpression);
            attachTemplateShortcuts(descriptors, template, stringLiteralExpression);
        }

        attachRouteShortcuts(descriptors, stringLiteralExpressiones);

        return descriptors.toArray(new FoldingDescriptor[descriptors.size()]);
    }


    private void attachRouteShortcuts(List<FoldingDescriptor> descriptors, Collection<StringLiteralExpression> stringLiteralExpressions) {

        Map<String,Route> routes = null;
        FoldingGroup group = FoldingGroup.newGroup("route");

        for(StringLiteralExpression stringLiteralExpression: stringLiteralExpressions) {

            if (MethodMatcher.getMatchedSignatureWithDepth(stringLiteralExpression, PhpRouteReferenceContributor.GENERATOR_SIGNATURES) != null) {

                // cache routes if we need them
                if(routes == null) {
                    Symfony2ProjectComponent symfony2ProjectComponent = stringLiteralExpression.getProject().getComponent(Symfony2ProjectComponent.class);
                    routes = symfony2ProjectComponent.getRoutes();
                }

                String contents = stringLiteralExpression.getContents();
                if(contents.length() > 0 && routes.containsKey(contents)) {
                    final Route route = routes.get(contents);

                    final String url = RouteHelper.getRouteUrl(route.getTokens());
                    if(url != null) {
                        descriptors.add(new FoldingDescriptor(stringLiteralExpression.getNode(),
                            new TextRange(stringLiteralExpression.getTextRange().getStartOffset() + 1, stringLiteralExpression.getTextRange().getEndOffset() - 1), group) {
                            @Nullable
                            @Override
                            public String getPlaceholderText() {
                                return url;
                            }
                        });
                    }
                }
            }

        }

    }

    private void attachModelShortcuts(List<FoldingDescriptor> descriptors, final FoldingGroup group, final StringLiteralExpression stringLiteralExpression) {

        if (MethodMatcher.getMatchedSignatureWithDepth(stringLiteralExpression, SymfonyPhpReferenceContributor.REPOSITORY_SIGNATURES) == null) {
            return;
        }

        String content = stringLiteralExpression.getContents();

        for(String lastChar: new String[] {":", "\\"}) {
            if(content.contains(lastChar)) {
                final String replace = content.substring(content.lastIndexOf(lastChar) + 1);
                if(replace.length() > 0) {
                    descriptors.add(new FoldingDescriptor(stringLiteralExpression.getNode(),
                        new TextRange(stringLiteralExpression.getTextRange().getStartOffset() + 1, stringLiteralExpression.getTextRange().getEndOffset() - 1), group) {
                        @Nullable
                        @Override
                        public String getPlaceholderText() {
                            return replace;
                        }
                    });
                }

                return;
            }
        }

    }

    private void attachTemplateShortcuts(List<FoldingDescriptor> descriptors, final FoldingGroup group, final StringLiteralExpression stringLiteralExpression) {

        if (MethodMatcher.getMatchedSignatureWithDepth(stringLiteralExpression, SymfonyPhpReferenceContributor.TEMPLATE_SIGNATURES) == null) {
            return;
        }

        String content = stringLiteralExpression.getContents();
        String templateShortcutName = null;

        if(content.endsWith(".html.twig") && content.length() > 10) {
            templateShortcutName = content.substring(0, content.length() - 10);
        } else if(content.endsWith(".html.php") && content.length() > 9) {
            templateShortcutName = content.substring(0, content.length() - 9);
        }

        if(templateShortcutName == null || templateShortcutName.length() == 0) {
            return;
        }

        final String finalTemplateShortcutName = templateShortcutName;
        descriptors.add(new FoldingDescriptor(stringLiteralExpression.getNode(),
            new TextRange(stringLiteralExpression.getTextRange().getStartOffset() + 1, stringLiteralExpression.getTextRange().getEndOffset() - 1), group) {
            @Nullable
            @Override
            public String getPlaceholderText() {
                return finalTemplateShortcutName;
            }
        });

    }

    @Nullable
    @Override
    public String getPlaceholderText(@NotNull ASTNode astNode) {
        return "...";
    }

    @Override
    public boolean isCollapsedByDefault(@NotNull ASTNode astNode) {
        return true;
    }
}
