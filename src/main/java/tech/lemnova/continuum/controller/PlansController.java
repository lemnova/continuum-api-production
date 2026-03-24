package tech.lemnova.continuum.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.lemnova.continuum.controller.dto.plan.PlanInfo;
import tech.lemnova.continuum.domain.plan.PlanConfiguration;
import tech.lemnova.continuum.domain.plan.PlanLimits;
import tech.lemnova.continuum.domain.plan.PlanType;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/plans")
public class PlansController {

    @Value("${stripe.price-id-plus:}")   private String priceIdPlus;
    @Value("${stripe.price-id-pro:}")    private String priceIdPro;
    @Value("${stripe.price-id-vision:}") private String priceIdVision;

    private final PlanConfiguration planConfig;

    public PlansController(PlanConfiguration planConfig) { this.planConfig = planConfig; }

    @GetMapping
    public ResponseEntity<List<PlanInfo>> list() {
        List<PlanInfo> out = new ArrayList<>();
        out.add(new PlanInfo(PlanType.FREE, planConfig.getLimits(PlanType.FREE), ""));
        out.add(new PlanInfo(PlanType.PLUS, planConfig.getLimits(PlanType.PLUS), priceIdPlus));
        out.add(new PlanInfo(PlanType.PRO,  planConfig.getLimits(PlanType.PRO),  priceIdPro));
        out.add(new PlanInfo(PlanType.VISION, planConfig.getLimits(PlanType.VISION), priceIdVision));
        return ResponseEntity.ok(out);
    }
}
