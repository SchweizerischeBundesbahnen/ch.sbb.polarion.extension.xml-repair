import type { SelectOption } from '@grigoriev/react-sbb-polarion';

// Mirrors: ch.sbb.polarion.extension.xml_repair.service.model.EntityType
export type EntityType = 'COLLECTION' | 'DOCUMENT' | 'WORKITEM';

// Mirrors: ch.sbb.polarion.extension.xml_repair.service.model.Issue
export interface Issue {
  metaInfo: string;
  repairer: string;
  description: string;
  warnings: string[];
  // Client-side annotation after repair
  repairResult?: RepairIssueResult;
}

// Mirrors: ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanEntity
export interface ScanEntity {
  entityType: EntityType;
  projectId: string;
  space: string | null;
  entityId: string;
  revision: string | null;
  issues: Issue[];
  fields: Record<string, Record<string, string>>;
  subitems: ScanEntity[];
  warnings: string[];
  // Client-side flag set after all issues are repaired
  repaired?: boolean;
}

// Mirrors: ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanResult
export interface ScanResult {
  items: ScanEntity[];
  report: string;
}

// Mirrors: ch.sbb.polarion.extension.xml_repair.service.model.scan.EntityRef
export interface EntityRef {
  space: string | null;
  id: string;
}

// Which of the two mutually exclusive filters the user drives the scan with: a selection of concrete
// entities (documents, collections) or a raw Lucene query. Work items only support the query.
export type FilterMode = 'SELECTION' | 'QUERY';

// Mirrors: ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanParams
export interface ScanParams {
  projectId: string;
  entityType: EntityType;
  entitySubtype: string | null;
  userQuery: string | null;
  entities: EntityRef[] | null;
  revision: string | null;
  sort: string | null;
  limit: number;
  timeout: number;
  repairers: string[];
  hideValid: boolean;
  configs: Record<string, Record<string, boolean>>;
}

// Mirrors: ch.sbb.polarion.extension.xml_repair.service.model.BaselineInfo
export interface BaselineInfo {
  revision: string;
  name: string | null;
}

// Mirrors: ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairParams
export interface RepairParams {
  issueMetaInfos: string[];
  configs: Record<string, Record<string, boolean>>;
}

// Mirrors: ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairResult
export interface RepairResult {
  issueMetaInfo: string;
  success: boolean;
  warnings: string[];
}

// Client-side result attached to individual issues after repair
export interface RepairIssueResult {
  success: boolean;
  warnings: string[];
}

// Mirrors: ch.sbb.polarion.extension.xml_repair.repairers.config.RepairerConfigMeta
export interface RepairerConfig {
  key: string;
  description: string;
  hint: string;
  type: 'BOOLEAN';
  defaultValue: boolean;
}

// Mirrors: ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairerMeta
export interface Repairer {
  id: string;
  name: string;
  description: string;
  configs: RepairerConfig[];
}

// Option of the shared SearchableSelect. Re-exported under the name this app already uses, so the
// option shape cannot drift from the component that renders it.
export type IconSelectOption = SelectOption;

// Entity subtype from Polarion enumeration API
export interface EntitySubtype {
  id: string;
  name: string;
  iconURL?: string;
}

// Mirrors: ch.sbb.polarion.extension.xml_repair.service.model.EntityInfo
export interface EntityInfo {
  space: string | null;
  id: string;
  name: string;
  type: string | null;
}
