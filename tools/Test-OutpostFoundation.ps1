param(
 [ValidateSet('ExplorationMenus','ExplorationCourier','ExplorationRestart','ExplorationLifecycle','ExplorationGathering','ExplorationRecords','ExplorationSupplies','ExplorationDiscovery','SurveyorSiteReview','ProductionWorkers','ProductionLayouts','ProductionProfiles','CampPreparationWater','CampPreparationWaterRestart','SurveyorWater','CampPreparationCliff','CampPreparationGap','CampPreparationHillRestart','CampPreparationHill','CampPreparationEndurance','CampPreparationRestart','CampPreparation','CampInventory','PreparedFoundingRestart','PreparedFoundingCancel','PreparedFoundingFlow','PreparedFounding','SurveyorBuilderPreparation','SurveyorPreparationWork','SurveyorPreparationRecords','SurveyorTerrain','SurveyorMenus','SurveyorExpedition','SurveyorSupplies','SurveyorCargo','SurveyorTickets','SurveyorHarvest','SurveyorRecords','SurveyorHut','FreeFounderPreCancel','FreeFounderRestart','FreeFounderMenu','FreeFounderCancel','FreeFounderCompletion','FreeFounderEndurance','FreeFounderFlow','FreeFounderPolicy','FreeFounderGrant','FreeFounderLayout','FreeFounderRecords','MayorExpansionForester','MayorExpansion','MayorUpgradeInFlight','MayorUpgradeRestart','MayorUpgrade','MayorProposals','RemoteEndurance','RemoteIllness','RemoteMenu','WarehouseRestart','WarehouseCompletion','WarehouseBootstrap','WarehouseInventory','WarehouseSites','RemoteSettlementCompletion','MayorNeeds','RemoteHousingCompletion','RemoteBuildingCompletion','MayorConstruction','MayorSupplyRestart','MayorSupply','MayorReservations','MayorPolicy','BudgetedSupplyNative','BudgetedSupply','RemoteConstructionRestart','RemoteConstructionOrders','RemoteLocality','MayorRoleRestart','MayorRole','FoundingFlowRestart','FoundingCancelRestart','FoundingCancel','FoundingFlow','FoundingPlacementRestart','FoundingPlacement','FoundingTravelRestart','FoundingTravel','FoundingEscrow','RemoteFounding','FoundingCargo','RemoteEndpoints','RemoteClaimRestart','RemoteActivity','RemoteClaims','RemoteSites','RemoteRecords','MenuProtocol','MenuRoutes','MenuDistricts','MenuRead','ForesterWithCouriers','CourierTravel','CourierRestart','ForesterSupply','CourierNative','CourierMembership','CourierDistricts','Arcology','Records','NativeRestart','Operations','Supply','SupplyRestart','SupplyBridge','SupplyNative','SupplyNativeRestart','ArcologyFull','ArcologyNavigation','ArcologyUpgrades','ArcologyBehavior','ArcologyLifecycle','ArcologyCrafting','ArcologySmelting','ArcologyArtisans','ArcologyFood','ArcologyCompost','ArcologyProcessors','ArcologyAnimals','ArcologyGathering','ArcologyCivic','ArcologyCivicGrave','ArcologySifter','ArcologyDefense','ArcologyMagic','ArcologyMixerWater','ArcologyRestaurant','ArcologyBarracks','ArcologyBeeProducts','ArcologyEnchanting','ArcologyChickenContainment','ArcologyStable','ArcologyStableCycles','ArcologyStableCapacity','ArcologyMountedStalls','ArcologyAnimalHome','ArcologyStableBreeding','ArcologyStableBreedingRestart','ArcologyStableBuild','ArcologyGatehouse','ArcologyQuarry','ArcologyCropExtensions','ArcologyGatehouseBuild','ArcologyTwistingVineProduction','ArcologyCropProduction','ArcologyCropActiveRestart','ArcologyBuilderRestart','ArcologyExtensionNavigation','ArcologyQuarryBounds','ArcologyQuarryBuild','ArcologyExtensionRestart','ArcologyPlantationTools','ArcologySeagrassProduction','ArcologyConnectedLayout','ArcologyCropBuild','ArcologyQuarryDepth','ArcologyAmendments','ArcologyLanternSafety','ArcologyCoralConstruction','ArcologyQuarryAccess','ArcologySeaPickleProduction','ArcologyFungusProduction','ArcologyQuarryRules','ArcologyCavalry','ArcologyQuarryRestart','ArcologyTransformedNavigation','ArcologyInteriorNavigation','ArcologyQuarryHazards','ArcologyDiscovery','ArcologyQuarryPools','ArcologyQuarryShortage','ArcologyQuarryRestock','ArcologyQuarryInflow')][string]$Suite='Arcology',
 [string]$ArcologyPath='blueprints/arcology-horizons',
 [string]$AmendmentsModsPath='',
 [ValidateSet('payment-pending','payment-native','timer','earned','delivery-pending','delivery-native','delivered')][string]$ExplorationFault='timer',
 [ValidateSet('intent','native','missing','one','two','three','four','return')][string]$FoundingFault='one',
 [ValidateRange(30,900)][int]$TimeoutSeconds=300
)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$runId=[guid]::NewGuid().ToString('N')
$reportRoot=Join-Path $projectRoot "build/reports/foundation/$runId"
$env:JAVA_HOME=if($env:JAVA_HOME){$env:JAVA_HOME}else{'C:/Program Files/Java/jdk-21'}
$testInstance=Join-Path $projectRoot "runs/foundation-sessions/$runId"
$packTarget=Join-Path $testInstance "blueprints/arcology"
New-Item -ItemType Directory -Force -Path $packTarget | Out-Null
Get-ChildItem -LiteralPath $ArcologyPath -Force | Copy-Item -Destination $packTarget -Recurse -Force
if($Suite -eq 'ArcologyAmendments' -and !$AmendmentsModsPath){throw 'ArcologyAmendments requires -AmendmentsModsPath'}
$integrationArgs=''
if($AmendmentsModsPath){
 $AmendmentsModsPath=(Resolve-Path -LiteralPath $AmendmentsModsPath).Path
 if($AmendmentsModsPath -match '["%&|<>^!\r\n]'){throw 'Unsupported integration path'}
 New-Item -ItemType Directory -Force -Path (Join-Path $testInstance 'config') | Out-Null
 Set-Content -LiteralPath (Join-Path $testInstance 'config/amendments-common.toml') -Value "[lantern]`nfalling_lanterns = `"ON`""
 $integrationArgs=' "-PamendmentsTestMods={0}"' -f $AmendmentsModsPath
}
$integrationArgs+=' "-PexplorationFault={0}"' -f $ExplorationFault
$phases=if($Suite -eq 'ExplorationRestart'){@('seed','reload')}elseif($Suite -in @('CampPreparationWaterRestart','CampPreparationHillRestart','CampPreparationRestart','PreparedFoundingRestart')){@('seed','reload')}elseif($Suite -eq 'FreeFounderRestart'){@("seed-$FoundingFault","reload-$FoundingFault")}elseif($Suite -eq 'MayorUpgradeInFlight'){@('seed','reload','paused-reload','complete-reload')}elseif($Suite -in @('MayorUpgradeRestart','WarehouseRestart','MayorSupplyRestart','RemoteConstructionRestart','MayorRoleRestart','RemoteClaimRestart','FoundingEscrow','FoundingTravelRestart','FoundingPlacementRestart','FoundingCancelRestart','FoundingFlowRestart')){@('seed','reload','paused-reload')}elseif($Suite -eq 'ArcologyBuilderRestart'){@('seed','verify_build','verify_upgrade')}elseif($Suite -in @('ArcologyLifecycle','ArcologyExtensionRestart','ArcologyQuarryRestart','ArcologyStableBreedingRestart','ArcologyCropActiveRestart')){@('seed','verify')}elseif($Suite -eq 'CourierRestart'){@('seed','verify','verify_final','verify_missing','verify_corrupt')}elseif($Suite -eq 'SupplyNativeRestart'){@('seed','verify','verify_final')}elseif($Suite -eq 'NativeRestart'){@('seed','verify','verify_paused')}elseif($Suite -eq 'SupplyRestart'){@('seed','verify','verify_paused','verify_final')}else{@('seed')}
foreach($phase in $phases){
 $phaseRoot=Join-Path $reportRoot $phase
 New-Item -ItemType Directory -Force -Path $phaseRoot | Out-Null
 $log=Join-Path $phaseRoot 'run.log'
 $stderr=Join-Path $phaseRoot 'stderr.log'
 # Paths are structured local CLI inputs; reject cmd metacharacters rather than interpolate executable text.
 foreach($value in @($projectRoot,$phaseRoot,$ArcologyPath,$testInstance,$log,$stderr)){if($value -match '["%&|<>^!\r\n]'){throw 'Unsupported character in runner path'}}
 # Redirect in cmd itself: Windows PowerShell asynchronous redirect callbacks can
 # stop draining the pipe during WaitForExit and deadlock verbose Gradle output.
 $argsText='/d /s /c ""{0}" runOutpostFoundation "-PfoundationSuite={1}" "-PprobeOutput={2}" "-PprobeWorld=foundation-{3}" "-PprobePhase={4}" "-ParcologyPath={5}" "-PfoundationRunDir={6}"{9} --no-daemon --no-watch-fs --project-cache-dir .setup/foundation-gradle-cache --console=plain > "{7}" 2> "{8}""' -f (Join-Path $projectRoot 'gradlew.bat'),$Suite,$phaseRoot,$runId,$phase,$packTarget,$testInstance,$log,$stderr,$integrationArgs
 $p=Start-Process -FilePath $env:ComSpec -ArgumentList $argsText -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru
 $handle=$p.Handle
 if(!$p.WaitForExit($TimeoutSeconds*1000)){& taskkill.exe /PID $p.Id /T /F | Out-Null;throw "Timed out: $log"}
 $p.WaitForExit()
 if($p.ExitCode -ne 0){throw "Gradle exit $($p.ExitCode): $log"}
 $report=Join-Path $phaseRoot "$Suite.json"
 if(!(Test-Path -LiteralPath $report)){throw "Missing report: $report"}
 $expected=switch($Suite){
  CampPreparationCliff {@('camp_before_huts','settlers_prepare_site','normal_founding_handoff','prepared_state_reload')}
  CampPreparationGap {@('camp_before_huts','settlers_prepare_site','normal_founding_handoff','prepared_state_reload')}
  CampPreparationWater {@('camp_before_huts','settlers_prepare_site','normal_founding_handoff','prepared_state_reload')}
  CampPreparationWaterRestart {if($phase -eq 'seed'){@('prepared_seed')}else{@('prepared_reload')}}
  CampPreparationHillRestart {if($phase -eq 'seed'){@('prepared_seed')}else{@('prepared_reload')}}
  CampPreparationHill {@('camp_before_huts','settlers_prepare_site','normal_founding_handoff','prepared_state_reload')}
  CampPreparationEndurance {@('camp_before_huts','settlers_prepare_site','normal_founding_handoff','prepared_state_reload')}
  CampPreparationRestart {@("prepared_$phase")}
  CampPreparation {@('camp_before_huts','settlers_prepare_site','normal_founding_handoff','prepared_state_reload')}
  CampInventory {@('unload_preserves_equipment','excess_dirt_unloaded','full_camp_no_mutation','partial_space_conserves_items','failed_intent_keeps_inventory','failed_flush_no_receipt')}
  SurveyorMenus {@('native_survey_tab','hut_snapshot','townhall_open_rejected','surveyor_open_allowed','remote_access_rejected','findings_survive_original_hut','findings_do_not_claim_production','empty_survey_has_no_resource_location','legacy_rediscover','missing_hut_rejected')}
  ExplorationMenus {@('locked_discover_only','unlocked_costs_and_yield','discovery_only_fruit','native_tabs_preserved','foreign_hut_denied','outpost_entry_removed','forged_yield_ignored','replayed_start_no_charge','stale_recall_rejected','destroyed_hut_denied')}
  ExplorationCourier {@('native_exploration_pickup','minimum_stock_preserved','native_jobs_unchanged')}
  ExplorationRestart {@("restart_$phase")}
  ExplorationLifecycle {@('owner_logout_pauses','owner_dimension_pauses','worker_paused_returns','worker_reassigned_returns','worker_ill_returns','ownership_change_locks','missing_worker_quarantines','recall_before_reward','recall_after_reward','hut_removed_holds_cargo','full_hut_holds_reward','wrong_reward_quarantines','early_reward_quarantines','missing_reward_quarantines','wrong_quantity_quarantines','repeat_requires_manual_restart')}
  ExplorationGathering {@('native_worker_departure','real_6000_tick_trip','exact_output_once','same_worker_returns','owner_stays_home','no_remote_tickets_or_edits','repeat_pays_each_trip','shortage_stops_repeat')}
  ExplorationRecords {@('all_exact_offers','completed_repeat_is_off','waiting_return_is_idempotent','wrong_list_type_rejected','completed_history_does_not_block','timer_preserves_command_revision','record_round_trip','exact_timer_boundary','invalid_record_bounds','stale_and_duplicate_records','one_active_per_colony','recall_stops_repeat','shared_expedition_exclusion')}
  ExplorationSupplies {@('exact_payment_and_wear','insufficient_wear_no_payment','last_trip_breaks_tool','shortage_no_payment','pending_write_failure','native_flush_failure_quarantines','cargo_defensive_copies','stale_apply_quarantines','final_receipt_failure_quarantines','missing_earned_reward_rejected')}
  ExplorationDiscovery {@('no_sample_no_unlock','pending_return_no_unlock','returning_locked','undelivered_cargo_locked','completed_return_unlocks','foreign_owner_colony_locked','evidence_round_trip','legacy_report_locked','forged_evidence_rejected','malformed_receipt_locked','missing_source_rejected')}
  SurveyorExpedition {@('paid_native_departure','actual_sample_delivered','original_worker_returns','owner_stays_home','no_settlement_site_search','actual_biome_and_resource','tickets_released')}
  SurveyorSupplies {@('finite_rations_and_tool','shortage_no_mutation','missing_tool_no_mutation','failed_save_no_withdrawal','interrupted_flush_quarantined')}
  SurveyorCargo {@('cargo_round_trip','defensive_item_copies','cargo_limits','pending_custody_rejected','harvest_receipt_persistence')}
  SurveyorTickets {@('bounded_inspections','one_request_per_tick','unexplored_chunk_loaded','release_all')}
  PreparedFoundingRestart {@("prepared_$phase")}
  PreparedFoundingCancel {@('cancel_paid_camp')}
  PreparedFoundingFlow {@('camp_before_huts','settlers_prepare_site','normal_founding_handoff','prepared_state_reload')}
  PreparedFounding {@('legacy_still_rejects_obstruction','prepared_site_accepts_builder_work','prepared_draft_survives_save','legacy_camp_without_barrels','shared_approach_clears_full_hill','shared_approach_rejects_excess_cut','scaled_camp_storage')}
  SurveyorBuilderPreparation {@('distant_builder_cannot_edit','occluded_builder_requests_approach','builder_steps_out_of_fill','builder_clears_fill_edge','native_builder_uses_own_inventory','completed_work_does_not_repeat')}
  SurveyorPreparationWork {@('paid_fill_once','missing_dirt_preserves_world','tool_and_real_drop','missing_tool_preserves_world','full_inventory_preserves_world','changed_container_preserved','failed_intent_changes_nothing','failed_native_save_no_receipt_or_replay','occupied_fill_rejected')}
  SurveyorPreparationRecords {@('pending_survives_reload','receipt_advances_once','cancel_keeps_completed_work','cancel_pending_requires_recovery','completion_limit','malformed_journal_rejected')}
  SurveyorSiteReview {@('terrain_candidate_read_only','candidate_not_founding_authority','candidate_wrong_owner_rejected','candidate_wrong_evidence_rejected','candidate_preserves_container','site_progress_round_trip','site_search_bounded','site_result_matches_inspected_column','site_saved_evidence_mismatch')}
  ProductionWorkers {@('exact_sapling_filter','exact_flower_filter','native_flower_level_gate','wrong_native_hut_rejected','plantation_job_identity')}
  ProductionLayouts {@('birch_log_layout','poppy_layout','wheat_seeds_layout','vine_layout','kelp_layout','seagrass_layout')}
  ProductionProfiles {@('profile_starter_stock','aquatic_operating_supplies','profile_grant_journal','legacy_grant_preserved','wood_exact_seed','flowers_native_levels','wheat_native_farmer','aquatic_field_identity','vine_field_identity','unsupported_target_rejected','unimplemented_seed_conversion_rejected','profile_round_trip','forged_profile_rejected')}
  SurveyorWater {@('contained_pool_costed','legacy_review_rejects_water','open_water_rejected','broken_wall_rejected','deep_water_rejected','flowing_water_rejected','aquatic_area_preserved','water_plants_preserved','changed_wall_detected','water_plan_round_trip','paid_water_fill_and_reload','breach_before_commit_preserves_dirt','unplanned_water_fill_rejected','missing_boundary_rejected_on_load','placement_callback_cannot_bypass_containment')}
  SurveyorTerrain {@('supported_dirt_fill','trees_and_hills_are_costed','deep_foundation_rejected','lava_rejected','container_rejected','constructed_blocks_rejected','excess_cut_rejected','overlap_not_double_charged','changed_site_detected','bounded_envelope','clear_columns_top_down','plan_round_trip')}
  SurveyorHarvest {@('requires_shears','real_sheared_drop','no_room_no_harvest','changed_block_rejected','intent_failure_preserves_block','harvest_cannot_replay')}
  SurveyorRecords {@('round_trip','unknown_resource_state','worker_identity','stale_mutation','bounded_search','one_active_per_colony','exact_resource_identity','duplicate_sample_receipt','sample_limit','ambiguous_sample_recovery')}
  SurveyorHut {@('native_hut_registered','hut_item_registered','building_registered','job_registered','native_florist_unchanged','native_tile_supported','native_assignment_and_save')}
  FreeFounderPreCancel {@("preview_no_creation","no_idle_home_workers","approval_no_creation","cancel_before_creation")}
  FreeFounderRestart {@("founders_$phase")}
  FreeFounderMenu {@('no_picker_required','free_four_copy','planned_vs_arrived','cap_blocker_readable','stale_approval_inert','foreign_owner_rejected','forged_roster_rejected','legacy_proposal_still_readable')}
  FreeFounderCancel {@('preview_no_creation','no_idle_home_workers','approval_no_creation','four_native_adults','retry_no_duplicate','grant_roster_reload','protected_arrival','four_distinct_arrivals','full_starter_inventory_waits','starter_delivery','native_mayor','forester_starter_kit','hut_food_without_rack_entry','cancel_returns_originals','cancel_keeps_entitlement','cancel_overlap_blocked','cancelled_hut_not_rehired','cancelled_site_stays_local')}
  FreeFounderCompletion {@('finite_native_build')}
  FreeFounderEndurance {@('finite_native_build')}
  FreeFounderFlow {@('preview_no_creation','no_idle_home_workers','approval_no_creation','four_native_adults','retry_no_duplicate','grant_roster_reload','protected_arrival','four_distinct_arrivals','full_starter_inventory_waits','starter_delivery','native_mayor','forester_starter_kit','hut_food_without_rack_entry')}
  FreeFounderPolicy {@('housing_not_required','server_cap','reservation_without_creation','duplicate_reservation','two_pending_reservations','concurrent_immigration','research_cap','unknown_receipt_id_rejected','interrupted_creation_quarantined','return_journal_strict_types','return_cursor_requires_targets','ambiguous_grant_does_not_block_home')}
  FreeFounderGrant {@('preview_no_creation','no_idle_home_workers','approval_no_creation','four_native_adults','retry_no_duplicate','grant_roster_reload')}
  FreeFounderLayout {@('eight_huts','four_homes_one_forester','builder_warehouse_courier_order','legacy_four_no_courier','legacy_five_two_foresters','roster_order_independent','incomplete_roster_no_authority')}
  FreeFounderRecords {@('resource_intent_requires_survey','resource_roster_roundtrip','resource_roster_wrong_role_rejected','legacy_roster_producer_rejected','survey_binding_roundtrip','survey_binding_legacy_rejected','survey_binding_invalid_rejected','legacy_four_roundtrip','legacy_five_roundtrip','free_intent_without_ids','free_roster_roundtrip','duplicate_identity_rejected','unknown_policy_rejected','reused_id_wrong_uuid','distinct_uuid_reused_id_activation')}
  FoundingCancelRestart {if($phase -eq 'seed'){@('unapproved_cancel','full_refund_waits','component_refund_once','empty_remote_claims_released','duplicate_cancel_inert','refund_journal_roundtrip')}else{@("cancel_$phase")}}
  FoundingCancel {@('unapproved_cancel','full_refund_waits','component_refund_once','empty_remote_claims_released','duplicate_cancel_inert','refund_journal_roundtrip')}
  FoundingFlowRestart {if($phase -eq 'seed'){@('shortage_before_claims','approved_finite_staging','reserved_founder_not_hired_home','approved_identity_locked','stale_approval_inert','offline_founding_paused','paid_staging_before_travel','full_starter_inventory_waits','founders_arrive_home_owner','finite_starter_distribution','starter_interruption_quarantined')}else{@("flow_$phase")}}
  MayorRole {@('shortage_before_claims','approved_finite_staging','reserved_founder_not_hired_home','approved_identity_locked','stale_approval_inert','offline_founding_paused','paid_staging_before_travel','full_starter_inventory_waits','founders_arrive_home_owner','finite_starter_distribution','starter_interruption_quarantined'); @('native_mayor_job','separate_mayor_station','unmanaged_hut_unchanged','arrived_workers_stay_local','replaced_station_stops_mayor','duplicate_mayor_inert','wrong_mayor_rejected','mayor_vacancy_preserves_site')}

  MayorRoleRestart {if($phase -eq 'seed'){@('shortage_before_claims','approved_finite_staging','reserved_founder_not_hired_home','approved_identity_locked','stale_approval_inert','offline_founding_paused','paid_staging_before_travel','full_starter_inventory_waits','founders_arrive_home_owner','finite_starter_distribution','starter_interruption_quarantined'); @('native_mayor_job','separate_mayor_station','unmanaged_hut_unchanged','arrived_workers_stay_local','replaced_station_stops_mayor','duplicate_mayor_inert','wrong_mayor_rejected','mayor_vacancy_preserves_site')}else{@("flow_$phase")}}
  FoundingFlow {@('shortage_before_claims','approved_finite_staging','reserved_founder_not_hired_home','approved_identity_locked','stale_approval_inert','offline_founding_paused','paid_staging_before_travel','full_starter_inventory_waits','founders_arrive_home_owner','finite_starter_distribution','starter_interruption_quarantined')}
  RemoteIllness {@('shortage_before_claims','approved_finite_staging','reserved_founder_not_hired_home','approved_identity_locked','stale_approval_inert','offline_founding_paused','paid_staging_before_travel','full_starter_inventory_waits','founders_arrive_home_owner','finite_starter_distribution','starter_interruption_quarantined'); @('care_requires_local_arrival','care_finite_pickup','care_pickup_idempotent','care_native_cure','care_hunger_recovery','care_pause_guard','care_jobless_request','care_jobless_request_reused','care_jobless_second_illness')}

  MayorConstruction {@('shortage_before_claims','approved_finite_staging','reserved_founder_not_hired_home','approved_identity_locked','stale_approval_inert','offline_founding_paused','paid_staging_before_travel','full_starter_inventory_waits','founders_arrive_home_owner','finite_starter_distribution','starter_interruption_quarantined'); @('requires_mayor','construction_dependencies','construction_obstruction','native_build_order','construction_idempotent','construction_journal','construction_pause_guard','construction_mayor_vacancy','construction_interruption','construction_cancel_preserved','mayor_material_budget')}
  RemoteConstructionOrders {@('shortage_before_claims','approved_finite_staging','reserved_founder_not_hired_home','approved_identity_locked','stale_approval_inert','offline_founding_paused','paid_staging_before_travel','full_starter_inventory_waits','founders_arrive_home_owner','finite_starter_distribution','starter_interruption_quarantined'); @('requires_mayor','construction_dependencies','construction_obstruction','native_build_order','construction_idempotent','construction_journal','construction_pause_guard','construction_mayor_vacancy','construction_interruption','construction_cancel_preserved')}
  RemoteConstructionRestart {if($phase -eq 'seed'){@('shortage_before_claims','approved_finite_staging','reserved_founder_not_hired_home','approved_identity_locked','stale_approval_inert','offline_founding_paused','paid_staging_before_travel','full_starter_inventory_waits','founders_arrive_home_owner','finite_starter_distribution','starter_interruption_quarantined'); @('requires_mayor','construction_dependencies','construction_obstruction','native_build_order','construction_idempotent','construction_journal','construction_pause_guard','construction_mayor_vacancy','construction_interruption')}else{@("flow_$phase")}}
  RemoteLocality {@('shortage_before_claims','approved_finite_staging','reserved_founder_not_hired_home','approved_identity_locked','stale_approval_inert','offline_founding_paused','paid_staging_before_travel','full_starter_inventory_waits','founders_arrive_home_owner','finite_starter_distribution','starter_interruption_quarantined'); @('eat_delivered_food_without_hut_path','no_home_service_path','local_path_allowed','local_work_orders_only','local_housing_only')}
  FoundingPlacementRestart {@("placement_$phase")}
  FoundingPlacement {@('native_anchor_bill','unpaid_anchor_rejected','foreign_anchor_rejected','occupied_anchor_rejected','seven_native_level_zero_huts','finite_anchor_conservation','duplicate_placement_inert','anchor_journal_roundtrip','interrupted_anchor_quarantined','built_site_cancel_preserves_effects')}
  FoundingTravelRestart {@("travel_$phase")}
  FoundingTravel {@('departure_preserves_originals','duplicate_departure_inert','offline_travel_paused','blocked_arrival_guarded','partial_arrival_retry','safe_arrival_once')}
  FoundingEscrow {@("escrow_$phase")}
  RemoteFounding {@('existing_party_identity','party_owner_rejected','party_child_rejected','party_busy_rejected','party_travelling_rejected','party_dead_rejected')}
  FoundingCargo {@('reserve_and_atomic_shortage','components_and_extract_rules','handoff_failure_quarantined','changed_inventory_rejected')}
  RemoteEndpoints {@('sixteen_endpoints','endpoint_overflow_blocks','cancelled_endpoints_released','ticket_failure_releases_acquired','partial_terrain_rejected')}
  RemoteClaimRestart {@("claims_$phase")}
  RemoteActivity {@('offline_no_tickets','bounded_production_tickets','reduced_runtime_limits','activity_reconciles_claims','pause_resume_tickets','zero_random_ticks','native_colony_active','natural_growth_from_home','logout_releases_tickets')}
  RemoteClaims {@('remote_claims_committed','home_and_gap_unchanged','duplicate_commit','claim_recovery','foreign_claim_preserved','conflict_stops_development')}
  WarehouseRestart {if($phase -eq 'seed'){@('finite_native_build')}else{@("warehouse_$phase")}}
  RemoteEndurance {@('finite_native_build')}
  WarehouseCompletion {@('finite_native_build')}
  WarehouseBootstrap {@('warehouse_bootstrap_bill')}
  WarehouseInventory {@('warehouse_rack_custody')}
  WarehouseSites {@('supported_remote_layout','supported_access','upgrade_envelopes','unsupported_floor','liquid_rejected','container_rejected','terrain_fingerprint','upgrade_fingerprint','home_radius_rejected','world_border_rejected','foreign_claim_rejected','pack_identity_and_missing','survey_cadence_reload','survey_bounded')}
  RemoteSites {@('supported_remote_layout','supported_access','upgrade_envelopes','unsupported_floor','liquid_rejected','container_rejected','terrain_fingerprint','upgrade_fingerprint','home_radius_rejected','world_border_rejected','foreign_claim_rejected','pack_identity_and_missing','survey_cadence_reload','survey_bounded')}
  RemoteRecords {@('legacy_no_authority','duplicate_and_stale_atomic','component_budget_roundtrip','defensive_copies','strict_schema','owner_limits','reduced_limits','invalid_authority_rejected','blocked_draft_rejected','pause_cannot_bypass_limits','disk_fail_closed')}
  MenuDistricts {@('rack_extends_boundary','townhall_rejected','unloaded_rejected','cross_assignment_rejected','stale_hut_rejected','multi_hut_draft','enclosed_hut_included','limits_rejected','missing_blueprint_rejected','foreign_owner_rejected','stale_review_rejected','missing_claim_rejected','activate_and_pause','overlap_rejected')}
  MenuRoutes {@('invalid_page_no_create','duplicate_create_once','same_district_rejected','limits_rejected','home_outpost_pair','held_components_preserved','compatible_binding_reused','empty_hand_rejected','pause_resume','foreign_colony_rejected','stop_with_cargo')}
  RemoteMenu {@('remote_owner_only','remote_party_limit','remote_requires_warehouse','remote_review_no_effects','remote_invalid_budget','remote_proposal_cannot_resume','remote_summary_truthful','remote_options_bounded','remote_shortage_no_orphan','remote_review_replay','remote_wire_copy','remote_coordinate_bounds')}
  MenuProtocol {@('close_bypasses_view','rate_limit','expired_session_rejected','round_trip','oversized_decode_rejected','unknown_session_rejected','session_actor_bound','duplicate_request_cached','reordered_request_rejected','changed_owner_rejected')}
  MenuRead {@('empty_route_settings','settings_copy','settings_reload','owner_only_snapshot','paged_snapshot','unloaded_snapshot')}
  CourierRestart {@("cargo_$phase")}
  ForesterSupply {@('finite_home_resupply','first_harvest_replant','native_log_deposit','export_home_receipt','food_consumed','second_cycle_pause_resume','forester_identity_and_bounds','replacement_axe_used','shortage_then_recovery','sapling_restock_used','full_export_destination','unloaded_export_destination')}
  ForesterWithCouriers {@('finite_home_resupply','first_harvest_replant','native_log_deposit','export_home_receipt','food_consumed','second_cycle_pause_resume','forester_identity_and_bounds','replacement_axe_used','shortage_then_recovery','sapling_restock_used','full_export_destination','unloaded_export_destination','local_warehouse_export_buffer')}
  CourierTravel {@('native_home_delivery','native_outpost_delivery','native_pause_resume')}
  CourierNative {@('warehouse_remote_stock_rejected','unmanaged_selection','local_resolver_allowed','remote_source_resolver_rejected','remote_target_resolver_rejected','remote_attempt_rejected','remote_enqueue_rejected','mixed_batch_selects_only_local','changed_claim_blocks_work','local_path_allowed','remote_detour_rejected','segment_crossing_rejected','native_navigation_rejects_detour','native_blocked_delivery_retains_cargo','native_blocked_pickup_preserves_stock','owner_change_retains_custody','warehouse_replacement_retains_custody','warehouse_rack_source_allowed','resume_ongoing_delivery_without_dump','completed_pickup_keeps_custody','managed_stuck_recovery_suppressed','unsafe_path_job_never_teleports','indirect_local_path_can_be_computed')}
  CourierMembership {@('native_unmanaged','planned_blocked','active_membership','paused_membership_refresh','unknown_hut','foreign_claim','unloaded_hut','active_roundtrip','legacy_stays_paused','native_marker_saved','marker_on_registration')}
  CourierDistricts {@('unmanaged_passthrough','home_local','outpost_local','cross_district','foreign_claim','dimension_mismatch','blocked_endpoint','mixed_unmanaged_rejected','missing_active_identity')}
  ArcologyGathering {@('farmer_cycle','beekeeper_cycle','fisherman_cycle','florist_cycle')}
  ArcologyCivicGrave {@('graveyard_recovery')}
  ArcologySifter {@('sifter_cycle')}
  ArcologyDefense {@('archery_cycle','combatacademy_cycle','guardtower_cycle')}
  ArcologyMagic {@('alchemist_brew_swiftness','enchanter_station_visit')}
  ArcologyMixerWater {@('mixer_water_1','mixer_water_2','mixer_water_3','mixer_water_4','mixer_water_5')}
  ArcologyRestaurant {@('cook_service')}
  ArcologyBarracks {@('barracks_tower_links','barrackstower_housing','barrackstower_duty')}
  ArcologyBeeProducts {@('beekeeper_honey_cycle','beekeeper_honeycomb_cycle')}
  ArcologyEnchanting {@('enchanter_ancient_tome')}
  ArcologyQuarryAccess {@('simplequarry_shaft_access','mediumquarry_shaft_access')}
  ArcologyQuarryDepth {@('simplequarry_assignment','mediumquarry_assignment','simplequarry_excavation','mediumquarry_excavation','simplequarry_full_depth','mediumquarry_full_depth','quarry_courier_hauling')}
  ArcologyStableCycles {@(1..5 | ForEach-Object {"stable_breeding_$_";"stable_bounded_herd_$_"})}
  ArcologyStableCapacity {@(1..5 | ForEach-Object {"stable_capacity_$_"})}
  ArcologyMountedStalls {@(foreach($tier in 1..5){foreach($stall in 1..(2*$tier)){"mounted_stall_${tier}_$stall"}};"mounted_stall_roundtrip")}
  ArcologyAnimalHome {@('pending_home_saved','home_resolves_after_building','explicit_home_clear','explicit_home_reassign','pending_home_removed','resolved_home_dirty','network_home_resolved')}
  ArcologyStableBreeding {@('stable_feed_shortage','stable_breeding')}
  ArcologyStableBreedingRestart {if($phase -eq 'seed'){@('stable_feed_shortage','breeding_seed')}else{@('breeding_verify')}}
  ArcologyBuilderRestart {@("builder_$phase")}
  ArcologyCropActiveRestart {@("crop_$phase")}
  ArcologyCavalry {@('cavalry_mount','cavalry_exit','cavalry_return')}
  ArcologyQuarryRestart {@("quarry_$phase")}
  ArcologyDiscovery {@('pack_metadata','all_blueprints_browsable','extensions_browsable')}
  ArcologyQuarryInflow {@('simplequarry_external_water','mediumquarry_external_lava')}
  ArcologyQuarryRestock {@('simplequarry_restock','mediumquarry_restock')}
  ArcologyQuarryShortage {@('simplequarry_fill_shortage','mediumquarry_fill_shortage')}
  ArcologyQuarryPools {@('simplequarry_water_pool','mediumquarry_lava_pool')}
  ArcologyQuarryHazards {@('simplequarry_water','simplequarry_lava','mediumquarry_water','mediumquarry_lava')}
  ArcologyQuarryRules {@('simplequarry_rules','mediumquarry_rules')}
  ArcologyFungusProduction {@('crimson_production','warped_production')}
  ArcologySeaPickleProduction {@('seapickle_production')}
  ArcologyAmendments {@('hanging_lantern','hanging_soul_lantern','standing_lantern','standing_soul_lantern','ordinary_support_physics','amendments_fire_control','amendments_builder_no_fire')}
  ArcologyLanternSafety {@('hanging_lantern','hanging_soul_lantern','standing_lantern','standing_soul_lantern','ordinary_support_physics')}
  ArcologyCoralConstruction {@('coral_construction_boundary')}
  ArcologyCropBuild {@('cocoa_construction','vine_construction','kelp_construction','seagrass_construction','seapickle_construction','glowberry_construction','weepingvine_construction','twistingvine_construction','crimson_construction','warped_construction')}
  ArcologyConnectedLayout {@('connected_timber_modules','connected_stone_modules','stacked_modules_external_stairs')}
  ArcologyPlantationTools {@('seagrass_tool_contract')}
  ArcologySeagrassProduction {@('seagrass_production')}
  ArcologyTwistingVineProduction {@('twistingvine_production')}
  ArcologyCropProduction {@('cocoa_production','vine_production','kelp_production','seagrass_production','seapickle_production','glowberry_production','weepingvine_production','twistingvine_production','crimson_production','warped_production')}
  ArcologyCropExtensions {@('cocoa_extension_contract','vine_extension_contract','kelp_extension_contract','seagrass_extension_contract','seapickle_extension_contract','glowberry_extension_contract','weepingvine_extension_contract','twistingvine_extension_contract','crimson_extension_contract','warped_extension_contract')}
  ArcologyQuarryBuild {@('upgrade:fundamentals/simplequarry1.blueprint','upgrade:fundamentals/mediumquarry1.blueprint')}
  ArcologyExtensionRestart {@("extensions_$phase")}
  ArcologyQuarryBounds {@('simplequarry_bounds','mediumquarry_bounds')}
  ArcologyQuarry {@('simplequarry_assignment','mediumquarry_assignment','simplequarry_excavation','mediumquarry_excavation')}
  ArcologyGatehouseBuild {@('upgrade:military/gatehouse1.blueprint','upgrade:military/gatehouse2.blueprint','upgrade:military/gatehouse3.blueprint')}
  ArcologyGatehouse {@('gatehouse_layout_1','gatehouse_layout_2','gatehouse_layout_3','gatehouse_knight_post','gatehouse_archer_post')}
  ArcologyStable {@('stable_layout_1','stable_layout_2','stable_layout_3','stable_layout_4','stable_layout_5','stablemaster_conversion','stablemaster_stall_delivery')}
  ArcologyStableBuild {@('upgrade:military/stable1.blueprint','upgrade:military/stable2.blueprint','upgrade:military/stable3.blueprint','upgrade:military/stable4.blueprint','upgrade:military/stable5.blueprint')}
  ArcologyChickenContainment {@('chicken_containment')}
  ArcologyCivic {@('library_learning','school_teaching','university_research','tavern_visitor','hospital_treatment','mysticalsite_happiness')}
  ArcologyAnimals {@('chickenherder_breed','cowboy_breed','shepherd_breed','rabbithutch_breed','swineherder_breed','chickenherder_eggs','cowboy_milk','shepherd_shear')}
  ArcologyProcessors {@('crusher_production','concretemixer_production','smeltery_production')}
  ArcologyCompost {@('composter_cycle')}
  ArcologyFood {@('baker_smelt','kitchen_smelt')}
  ArcologyArtisans {@('blacksmith_craft','mechanic_craft','dyer_craft','courier_delivery')}
  ArcologySmelting {@('glassblower_smelt','stonesmeltery_smelt')}
  ArcologyCrafting {@('sawmill_craft','stonemason_craft','fletcher_craft','courier_delivery')}
  ArcologyLifecycle {
   if($phase -eq 'seed'){@('fundamentals/miner','mystic/netherworker','agriculture/horticulture/plantation') | ForEach-Object { $prefix=$_; 1..5 | ForEach-Object { "upgrade:$prefix$_.blueprint" } }}
   @('miner_shaft','nether_expedition','plantation_crops')
  }
  ArcologyBehavior {@('miner_shaft','nether_expedition','plantation_crops')}
  ArcologyUpgrades { @('fundamentals/miner','mystic/netherworker','agriculture/horticulture/plantation') | ForEach-Object { $prefix=$_; 1..5 | ForEach-Object { "upgrade:$prefix$_.blueprint" } } }
  ArcologyInteriorNavigation {@('sealed_stall_control'); @('military/stable1.blueprint','military/stable2.blueprint','military/stable3.blueprint','military/stable4.blueprint','military/stable5.blueprint','military/gatehouse1.blueprint','military/gatehouse2.blueprint','military/gatehouse3.blueprint') | ForEach-Object { $path=$_; @('NONE','R90','R180','R270','MIR_NONE','MIR_R90','MIR_R180','MIR_R270') | ForEach-Object { 'navigation:'+$path+':'+$_ } }}
  ArcologyTransformedNavigation {@('military/stable1.blueprint','military/stable2.blueprint','military/stable3.blueprint','military/stable4.blueprint','military/stable5.blueprint','military/gatehouse1.blueprint','military/gatehouse2.blueprint','military/gatehouse3.blueprint','fundamentals/simplequarry1.blueprint','fundamentals/mediumquarry1.blueprint','infrastructure/plantationfields/cocoa1.blueprint','infrastructure/plantationfields/vine1.blueprint','infrastructure/plantationfields/kelp1.blueprint','infrastructure/plantationfields/seagrass1.blueprint','infrastructure/plantationfields/seapickle1.blueprint','infrastructure/plantationfields/glowberry1.blueprint','infrastructure/plantationfields/weepingvine1.blueprint','infrastructure/plantationfields/twistingvine1.blueprint','infrastructure/plantationfields/crimson1.blueprint','infrastructure/plantationfields/warped1.blueprint') | ForEach-Object { $path=$_; @('NONE','R90','R180','R270','MIR_NONE','MIR_R90','MIR_R180','MIR_R270') | ForEach-Object { 'navigation:'+$path+':'+$_ } }}
  ArcologyExtensionNavigation {@('navigation:military/stable1.blueprint','navigation:military/stable2.blueprint','navigation:military/stable3.blueprint','navigation:military/stable4.blueprint','navigation:military/stable5.blueprint','navigation:military/gatehouse1.blueprint','navigation:military/gatehouse2.blueprint','navigation:military/gatehouse3.blueprint','navigation:fundamentals/simplequarry1.blueprint','navigation:fundamentals/mediumquarry1.blueprint','navigation:infrastructure/plantationfields/cocoa1.blueprint','navigation:infrastructure/plantationfields/vine1.blueprint','navigation:infrastructure/plantationfields/kelp1.blueprint','navigation:infrastructure/plantationfields/seagrass1.blueprint','navigation:infrastructure/plantationfields/seapickle1.blueprint','navigation:infrastructure/plantationfields/glowberry1.blueprint','navigation:infrastructure/plantationfields/weepingvine1.blueprint','navigation:infrastructure/plantationfields/twistingvine1.blueprint','navigation:infrastructure/plantationfields/crimson1.blueprint','navigation:infrastructure/plantationfields/warped1.blueprint')}
  ArcologyNavigation { @((Get-Content (Join-Path $projectRoot "src/main/resources/qa/arcology-building-anchors.json") -Raw | ConvertFrom-Json).PSObject.Properties.Name | ForEach-Object { "navigation:" + $_ }) }
  ArcologyFull {@(Get-ChildItem -LiteralPath $packTarget -Filter *.blueprint -Recurse | ForEach-Object { 'asset:' + $_.FullName.Substring((Resolve-Path -LiteralPath $packTarget).Path.Length+1).Replace('\','/') })}
  Arcology {@('required_files_present','native_blueprint_decode','required_hut_anchors','missing_pack_reported','invalid_blueprint_reported','terminal_availability_reported')}
  Records {@('multiple_huts_roundtrip','repeated_roles_distinct','worker_membership_exclusive','different_colonies_same_citizen_id','dimension_mismatch_rejected','stacked_nonoverlap','conflicting_volume_rejected','invalid_record_atomic','unknown_schema_rejected','malformed_data_rejected','native_identity_matches','foreign_colony_rejected','removed_hut_reported','missing_citizen_reported','unloaded_reference_deferred')}
  SupplyNativeRestart {@("native_$phase")}
  SupplyBridge {@('transient_snapshot_lock_retried','permanent_snapshot_failure_not_retried','snapshot_lock_retry_bounded','external_stock_reserve_and_delivery','slot_rules_and_changed_inventory','shared_source_reserve','shared_destination_target','changed_endpoint_prevents_all_writes','durable_handoff_order','handoff_failure_quarantined')}
  SupplyNative {@('registered_owner_commands','native_dispatch_reserve','native_delivery_reload_once','native_return_cargo','replaced_hut_retains_cargo','foreign_owner_rejected','resolver_does_not_load_chunks','failed_native_save_quarantined')}
  SupplyRestart {@("supply_$phase") }
  MayorSupplyRestart {if($phase -eq 'seed'){@('shortage_before_claims','approved_finite_staging','reserved_founder_not_hired_home','approved_identity_locked','stale_approval_inert','offline_founding_paused','paid_staging_before_travel','full_starter_inventory_waits','founders_arrive_home_owner','finite_starter_distribution','starter_interruption_quarantined'); @('reservation_needs_mayor','native_reservation_once','native_reservation_budget','reservation_keeps_native_stock','remote_courier_boundary','managed_supply_dispatch','managed_supply_offline')}else{@("flow_$phase")}}
  RemoteSettlementCompletion {@('finite_native_build')}
  RemoteHousingCompletion {@('finite_native_build')}
  RemoteBuildingCompletion {@('finite_native_build')}
  MayorNeeds {@('shortage_before_claims','approved_finite_staging','reserved_founder_not_hired_home','approved_identity_locked','stale_approval_inert','offline_founding_paused','paid_staging_before_travel','full_starter_inventory_waits','founders_arrive_home_owner','finite_starter_distribution','starter_interruption_quarantined'); @('native_food_request','native_food_arrival','native_tool_request','native_tool_arrival','operating_request_idempotent')}
  MayorSupply {@('shortage_before_claims','approved_finite_staging','reserved_founder_not_hired_home','approved_identity_locked','stale_approval_inert','offline_founding_paused','paid_staging_before_travel','full_starter_inventory_waits','founders_arrive_home_owner','finite_starter_distribution','starter_interruption_quarantined'); @('reservation_needs_mayor','native_reservation_once','native_reservation_budget','reservation_keeps_native_stock','remote_courier_boundary','managed_supply_dispatch','managed_supply_offline','managed_supply_reload','reservation_offline_pause')}
  MayorReservations {@('shared_home_reserve','stable_reservation','spent_not_double_counted','cancel_releases_only_unspent','founding_already_charged','reservation_components','reservation_reload','reservation_corrupt_rejected','reservation_server_checkpoint')}
  MayorUpgradeInFlight {@("inflight_$phase")}
  MayorExpansionForester {@('finite_native_build')}
  MayorExpansion {@('finite_native_build')}
  MayorUpgrade {@('finite_native_build')}
  MayorUpgradeRestart {if($phase -eq 'seed'){@('finite_native_build')}else{@("warehouse_$phase")}}
  MayorProposals {@('proposal_no_implicit_approval','proposal_stable_identity','proposal_changed_review_rejected','proposal_owner_and_revision','proposal_explicit_approval','proposal_denial_persists','proposal_reload','proposal_invalid_bounds','proposal_corrupt_reload')}
  MayorPolicy {@('reserve_math','incoming_demand','approval_proposes_once_identity','automatic_scope_and_budget','priority_operating_before_expansion','components_and_accepted_construction','empty_and_invalid_candidates')}
  BudgetedSupplyNative {@('native_budget_identity','native_budget_owner','native_budget_dispatch','native_budget_reload','native_budget_exhausted','native_budget_repeat_cannot_refill')}
  BudgetedSupply {@('finite_refill_budget','budget_route_idempotent','budget_transit_reload','return_does_not_refill_budget','budget_components','corrupt_budget_rejected')}
  Supply {@('two_way_delivery','full_destination','unavailable_endpoints','reserves_and_incoming','pause_and_return','components_and_copies','authority_and_bounds','snapshot_conservation','invalid_saved_data','returning_stock_counts_as_incoming','malformed_custody_rejected','failed_snapshot_write_preserves_custody')}
  Operations {@('supported_layout','native_builder_completion','worker_access','two_foresters_harvest_replant','local_supply_boundary')}
  NativeRestart {@("foundation_$phase")}
 }
 $rows=@(foreach($row in (Get-Content -LiteralPath $report -Raw | ConvertFrom-Json)){$row})
 if($rows.Count -ne $expected.Count -or @($rows | Where-Object outcome -ne PASS).Count -gt 0){throw "Invalid/failed cases: $report"}
 foreach($id in $expected){if(@($rows | Where-Object caseId -eq $id).Count -ne 1){throw "Missing/duplicate $id"}}
 if($Suite -in @("MayorUpgradeInFlight","MayorUpgradeRestart","WarehouseRestart","MayorSupplyRestart","RemoteConstructionRestart","MayorRoleRestart","FoundingFlowRestart","FoundingCancelRestart","FoundingPlacementRestart","FoundingTravelRestart","FoundingEscrow","RemoteClaimRestart","NativeRestart","SupplyRestart","ArcologyLifecycle","ArcologyExtensionRestart")){& (Join-Path $PSScriptRoot "Assert-FoundationPrimaryLoad.ps1") -Paths @($log,$stderr)}
 Write-Host "PASS $Suite/$phase ($($rows.Count) cases): $report"
}
