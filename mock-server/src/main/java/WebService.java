import io.javalin.Javalin;
import io.javalin.http.Handler;
import io.javalin.http.HandlerType;
import load.JsonLoader;
import load.Loader;
import load.LoaderException;
import load.YamlLoader;
import model.Component;
import model.InOutRule;
import model.OutInRule;
import model.Rule;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

@WebServlet(urlPatterns = {"/*"})
public class WebService extends HttpServlet {
    private List<Rule> rules = new ArrayList<>();
    private Javalin app;
    private Map<String, InOutHandler> handlers = new HashMap<>();

    public WebService() {
        app = Javalin.createStandalone();
        app.exception(LoaderException.class, ExceptionHandlers.genericHandler(400));
        app.exception(RuleAlreadyExistsException.class, ExceptionHandlers.genericHandler(400));
        app.exception(Exception.class, (exception, ctx) -> {
            exception.printStackTrace();
            ctx.status(500);
            ctx.result(List.of(exception.getClass().toString(), (exception.getMessage() != null) ? exception.getMessage() : "").toString());
        });

        app.get("/", ctx -> ctx.result("It works !"));
        app.post("/rules", addRulesHandler());
        app.post("/attack",attackHandler());
    }

    private Handler attackHandler() {
        return ctx -> {
            if(getRules().size() != 0) {
                Attacker attacker = new Attacker(getRules());
                ctx.status(204);
                if(Objects.equals(ctx.queryParam("type"), "all")) {
                    attacker.XSSAttacks();
                    attacker.httpFloodAttack();
                    attacker.robustnessAttacks();
                    attacker.requestSplittingAttack();
                } else if(Objects.equals(ctx.queryParam("type"), "httpflood")) {
                    attacker.httpFloodAttack();
                } else if(Objects.equals(ctx.queryParam("type"), "xss")) {
                    attacker.XSSAttacks();
                } else if(Objects.equals(ctx.queryParam("type"),"robustness")) {
                    attacker.robustnessAttacks();
                } else if(Objects.equals(ctx.queryParam("type"),"reqsplitting")) {
                    attacker.requestSplittingAttack();
                } else {
                    ctx.result("Error: wrong/no attack type given.");
                    ctx.status(400);
                }
                attacker.attack();
            } else {
                ctx.result("Error: no rules found.");
                ctx.status(400);
            }
        };
    }

    private Handler addRulesHandler() {
        return ctx -> {
            Loader loader;
            if (Objects.equals(ctx.header("Content-Type"), "text/yaml")) {
                loader = new YamlLoader();
            }
            else if (Objects.equals(ctx.header("Content-Type"), "application/json")){
                loader = new JsonLoader();
            }
            else {
                throw new LoaderException("Wrong content type");
            }
            List<Rule> rules = loader.load(ctx.body());
            initRules(rules);
            ctx.status(204);
        };
    }

    private void initRules(List<Rule> rules) throws RuleAlreadyExistsException {
        for (Rule rule: rules) {
            if (rule instanceof InOutRule) {
                addHandler((InOutRule) rule);
            } else if (rule instanceof OutInRule) {
                this.rules.add(rule);
                new OutputRequest((OutInRule) rule).start();
            }
        }
    }

    private void addHandler(InOutRule rule) throws RuleAlreadyExistsException {
        String simplePath = rule.getRequest().getPath().split("\\?")[0];
        String id = rule.getRequest().getMethod() + simplePath;
        if (handlers.containsKey(id)) {
            handlers.get(id).addRule(rule);
        } else {
            InOutHandler handler = new InOutHandler();
            handler.addRule(rule);
            try {
                app.addHandler(HandlerType.valueOf(rule.getRequest().getMethod()), simplePath, handler);
            } catch (IllegalArgumentException e) {
                throw new RuleAlreadyExistsException(String.format("The route '%s -> %s' cannot be created.",rule.getRequest().getMethod(), simplePath));
            }
            handlers.put(id, handler);
        }
    }

    private List<Rule> getRules() {
        return rules;
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        app.servlet().service(req, resp);
    }
}
