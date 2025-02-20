import { EtmLogsGroupComponent } from '../elastest-etm/etm-monitoring-view/etm-logs-group/etm-logs-group.component';
import { EtmChartGroupComponent } from '../elastest-etm/etm-monitoring-view/etm-chart-group/etm-chart-group.component';
import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { FlexLayoutModule } from '@angular/flex-layout';
import {
  CovalentDataTableModule,
  CovalentMediaModule,
  CovalentLoadingModule,
  CovalentNotificationsModule,
  CovalentLayoutModule,
  CovalentMenuModule,
  CovalentPagingModule,
  CovalentSearchModule,
  CovalentStepsModule,
  CovalentCommonModule,
  CovalentDialogsModule,
  CovalentMessageModule,
} from '@covalent/core';
import {
  MatButtonModule,
  MatCardModule,
  MatIconModule,
  MatListModule,
  MatMenuModule,
  MatTooltipModule,
  MatSlideToggleModule,
  MatInputModule,
  MatCheckboxModule,
  MatToolbarModule,
  MatSnackBarModule,
  MatSidenavModule,
  MatTabsModule,
  MatSelectModule,
  MatProgressSpinnerModule,
} from '@angular/material';
import { NgxChartsModule } from '@swimlane/ngx-charts';
import { LogsViewComponent } from './logs-view/logs-view.component';
import { MetricsViewComponent } from './metrics-view/metrics-view.component';
import { LoadPreviousViewComponent } from './load-previous-view/load-previous-view.component';
import { ParametersViewComponent } from './parameters-view/parameters-view.component';
import { MetricsChartCardComponent } from './metrics-view/metrics-chart-card/metrics-chart-card.component';
import { ComboChartComponent } from './metrics-view/metrics-chart-card/combo-chart/combo-chart.component';
import { CovalentExpansionPanelModule } from '@covalent/core';
import { TooltipAreaComponent } from './metrics-view/metrics-chart-card/combo-chart/components/tooltip-area.component';
import { TimelineComponent } from './metrics-view/metrics-chart-card/combo-chart/components/timeline.component';
import { VncClientComponent } from './vnc-client/vnc-client.component';
import { TestVncComponent } from './vnc-client/test-vnc/test-vnc.component';
import { BreadcrumbComponent } from './breadcrumb/breadcrumb.component';
import { LogsViewTextComponent } from './logs-view-text/logs-view-text.component';
import { StringListViewComponent } from './string-list-view/string-list-view.component';
import { MultiConfigViewComponent } from './multi-config-view/multi-config-view.component';
import { SelfAdjustableCardComponent } from './ng-self-adjustable-components/self-adjustable-card/self-adjustable-card.component';

import { RedirectComponent } from './redirect/redirect.component';
import { ButtonComponentComponent } from './button-component/button-component.component';
import { AutoHeightGridComponent } from './ng-self-adjustable-components/auto-height-grid/auto-height-grid.component';
import { NormalHeightRowComponent } from './ng-self-adjustable-components/auto-height-grid/normal-height-row/normal-height-row.component';
import { AutoHeightRowComponent } from './ng-self-adjustable-components/auto-height-grid/auto-height-row/auto-height-row.component';
import { CenteredElementComponent } from './centered-element/centered-element.component';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faFileDownload } from '@fortawesome/free-solid-svg-icons';
import { library } from '@fortawesome/fontawesome-svg-core';
import { ElastestLogComparatorComponent } from '../elastest-log-comparator/elastest-log-comparator.component';
import { ReportComparisonComponent } from '../elastest-log-comparator/report-comparison/report-comparison.component';

library.add(faFileDownload);

const FLEX_LAYOUT_MODULES: any[] = [FlexLayoutModule];

const ANGULAR_MODULES: any[] = [FormsModule, ReactiveFormsModule];

const MATERIAL_MODULES: any[] = [
  MatButtonModule,
  MatCardModule,
  MatIconModule,
  MatListModule,
  MatMenuModule,
  MatTooltipModule,
  MatSlideToggleModule,
  MatInputModule,
  MatCheckboxModule,
  MatToolbarModule,
  MatSnackBarModule,
  MatSidenavModule,
  MatTabsModule,
  MatSelectModule,
];

const COVALENT_MODULES: any[] = [
  CovalentDataTableModule,
  CovalentMediaModule,
  CovalentLoadingModule,
  CovalentNotificationsModule,
  CovalentLayoutModule,
  CovalentMenuModule,
  CovalentPagingModule,
  CovalentSearchModule,
  CovalentStepsModule,
  CovalentCommonModule,
  CovalentMessageModule,
  CovalentDialogsModule,
];

const CHART_MODULES: any[] = [NgxChartsModule];

@NgModule({
  imports: [
    CommonModule,
    ANGULAR_MODULES,
    MATERIAL_MODULES,
    COVALENT_MODULES,
    CHART_MODULES,
    FLEX_LAYOUT_MODULES,
    CovalentExpansionPanelModule,
    FontAwesomeModule,
    MatProgressSpinnerModule,
  ],
  declarations: [
    LogsViewComponent,
    MetricsViewComponent,
    LoadPreviousViewComponent,
    ParametersViewComponent,
    MetricsChartCardComponent,
    ComboChartComponent,
    EtmChartGroupComponent,
    TooltipAreaComponent,
    TimelineComponent,
    EtmLogsGroupComponent,
    ElastestLogComparatorComponent,
    ReportComparisonComponent,
    VncClientComponent,
    TestVncComponent,
    StringListViewComponent,
    BreadcrumbComponent,
    LogsViewTextComponent,
    MultiConfigViewComponent,
    SelfAdjustableCardComponent,
    RedirectComponent,
    ButtonComponentComponent,
    AutoHeightGridComponent,
    NormalHeightRowComponent,
    AutoHeightRowComponent,
    CenteredElementComponent,
  ],
  exports: [
    ANGULAR_MODULES,
    MATERIAL_MODULES,
    COVALENT_MODULES,
    CHART_MODULES,
    FLEX_LAYOUT_MODULES,
    LogsViewComponent,
    MetricsViewComponent,
    MetricsChartCardComponent,
    LoadPreviousViewComponent,
    ParametersViewComponent,
    MultiConfigViewComponent,
    SelfAdjustableCardComponent,
    StringListViewComponent,
    ComboChartComponent,
    EtmChartGroupComponent,
    EtmLogsGroupComponent,
    TooltipAreaComponent,
    TimelineComponent,
    VncClientComponent,
    TestVncComponent,
    RedirectComponent,
    BreadcrumbComponent,
    LogsViewTextComponent,
    AutoHeightGridComponent,
    NormalHeightRowComponent,
    AutoHeightRowComponent,
    CenteredElementComponent,
  ],
})
export class SharedModule {}
