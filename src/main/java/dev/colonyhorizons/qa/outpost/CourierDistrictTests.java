package dev.colonyhorizons.qa.outpost;

import dev.colonyhorizons.supply.CourierDistrictPolicy;
import dev.colonyhorizons.supply.CourierDistrictPolicy.*;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
public final class CourierDistrictTests {
    private static Endpoint active(District district) {
        return new Endpoint(Status.ACTIVE, Optional.of(district), "");
    }

    @GameTest(template="empty", templateNamespace="colonyhorizons", timeoutTicks=200)
    public static void district_contract(GameTestHelper h) throws Exception {
        var rows = new ArrayList<ProbeResult>();
        var home = active(new District(Level.OVERWORLD, 1, Optional.empty()));
        var remote = active(new District(Level.OVERWORLD, 1, Optional.of(new UUID(0, 1))));
        var other = active(new District(Level.OVERWORLD, 1, Optional.of(new UUID(0, 2))));
        var unmanaged = new Endpoint(Status.UNMANAGED, Optional.empty(), "");
        var blocked = new Endpoint(Status.BLOCKED, Optional.empty(), "Missing membership");
        SupplyTests.run(rows,"unmanaged_passthrough",()->check(true,unmanaged,unmanaged,unmanaged,unmanaged));
        SupplyTests.run(rows,"home_local",()->check(true,home,home,home,home));
        SupplyTests.run(rows,"outpost_local",()->check(true,remote,remote,remote,remote));
        SupplyTests.run(rows,"cross_district",()->{
            check(false,home,home,home,remote);
            check(false,remote,remote,remote,home);
            check(false,remote,remote,remote,other);
            check(false,home,home,remote,home);
            check(false,home,remote,home,home);
        });
        SupplyTests.run(rows,"foreign_claim",()->check(false,home,home,home,
            active(new District(Level.OVERWORLD,2,Optional.empty()))));
        SupplyTests.run(rows,"dimension_mismatch",()->check(false,home,home,home,
            active(new District(Level.NETHER,1,Optional.empty()))));
        SupplyTests.run(rows,"blocked_endpoint",()->{
            check(false,blocked,home,home,home); check(false,home,blocked,home,home);
            check(false,home,home,blocked,home); check(false,home,home,home,blocked);
        });
        SupplyTests.run(rows,"mixed_unmanaged_rejected",()->{
            check(false,home,home,unmanaged,home);check(false,unmanaged,home,home,home);
            check(false,unmanaged,unmanaged,unmanaged,blocked);
        });
        SupplyTests.run(rows,"missing_active_identity",()->check(false,home,home,home,
            new Endpoint(Status.ACTIVE,Optional.empty(),"")));
        ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"CourierDistricts",rows);
        h.assertTrue(rows.stream().allMatch(r->r.outcome()==ProbeResult.Outcome.PASS),"District policy: "+rows);
        h.succeed();
    }

    private static void check(boolean expected, Endpoint work, Endpoint warehouse, Endpoint source, Endpoint target) {
        var decision=CourierDistrictPolicy.check(work,warehouse,source,target);
        if(decision.allowed()!=expected)throw new AssertionError("Expected allowed="+expected+" for "+List.of(work,warehouse,source,target));
        if(!expected && decision.reason().isBlank())throw new AssertionError("Blocked work needs a reason");
    }
}
