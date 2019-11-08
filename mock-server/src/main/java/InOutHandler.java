import io.javalin.http.Context;
import io.javalin.http.Handler;
import model.InOutRule;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class InOutHandler implements Handler {
    private Set<InOutRule> rules = new HashSet<>();

    public void addRule(InOutRule rule) {
        rules.add(rule);
    }

    public void removeRule(InOutRule rule) {
        rules.remove(rule);
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        for (InOutRule rule : rules) {
            String path = (ctx.queryString() != null) ? String.format("%s?%s", ctx.path(), ctx.queryString()) : rule.getRequest().getPath();
            final boolean[] doesHeadersMatch = {true};
            rule.getRequest().getHeaders().forEach((s, s2) -> {
                if (!ctx.headerMap().get(s).equals(s2)) {
                    doesHeadersMatch[0] = false;
                }
            });
            if (ctx.body().equals(rule.getRequest().getBody())
                    && path.equals(rule.getRequest().getPath())
                    && doesHeadersMatch[0]
            ) {
                ctx.status(rule.getResponse().getStatus());
                ctx.result(rule.getResponse().getBody());
                rule.getResponse().getHeaders().forEach(ctx::header);
                return;
            }
        }
        ctx.status(400);
        ctx.result("Request not matching expectations.");
    }
}
