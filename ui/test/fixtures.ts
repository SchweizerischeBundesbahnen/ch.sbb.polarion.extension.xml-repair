import type { EntityType, Repairer, ScanResult } from '../src/types';

// ---------------------------------------------------------------------------------------------------
// Repairer fixtures = a verbatim mirror of the Java side, NOT invented data.
//
// The `/repairers?entityType=<type>` endpoint returns `XmlRepairPolarionService#getRepairerMetas`, i.e.
// one `RepairerMeta` per entry of `XmlRepairPolarionService.REPAIRERS` (the three EntityType -> repairer
// mappings), built from each repairer's `getRepairerId()` / `getDisplayName()` / `getDescription()` /
// `getConfigs()`. The fixtures below reproduce that 1:1 so the tests (and the committed visual
// references) show exactly what a user sees in Polarion.
//
// How to re-sync after a Java change - keep it mechanical, in this order:
//   1. src/.../repairers/<X>Repairer.java  -> one `const` below: id = class simple name, name = NAME,
//      description = getDescription() (concatenated into one string), configs = getConfigs() in order
//      (RepairerConfigMeta field order is key, description, hint, type, defaultValue).
//   2. XmlRepairPolarionService.REPAIRERS   -> the three arrays in REPAIRERS_BY_ENTITY_TYPE, same order
//      as the Java `List.of(...)`, since the UI renders the repairer cards in the returned order.
//   3. Re-run the behavior suite (names/counts are asserted) and regenerate the visual references with
//      `npm run test:update:docker`.
// ---------------------------------------------------------------------------------------------------

// Shared by ModuleContentLinksRepairer and FieldsRichTextLinksRepairer (both take BaseLinksRepairer's
// two options), mirroring the fact that both Java classes return the same pair of RepairerConfigMeta.
const LINK_CONFIGS: Repairer['configs'] = [
  {
    key: 'convertToPlainText',
    description: 'Convert unresolvable links to plain text',
    hint: 'Replace items which cannot be found by the specified ID in any available project with a plain text',
    type: 'BOOLEAN',
    defaultValue: false,
  },
  {
    key: 'adjustWorkItemPrefix',
    description: 'Adjust workitem-prefix',
    hint: 'Replace workitem prefix if a workitem with the given number exists in the current project',
    type: 'BOOLEAN',
    defaultValue: false,
  },
];

const BROKEN_LINKED_WORK_ITEMS: Repairer = {
  id: 'BrokenLinkedWorkItemsRepairer',
  name: 'Broken Work Item Links',
  description: 'Finds broken work item links. Repairer attempts to find and rewrite work items by ID.',
  configs: [
    {
      key: 'deleteUnresolvable',
      description: 'Delete broken items',
      hint: 'Delete items with a wrong link role or unresolvable link (linked item cannot be found by the specified data in any available project)',
      type: 'BOOLEAN',
      defaultValue: false,
    },
  ],
};

const FIELDS_FORMATTING_SYMBOLS: Repairer = {
  id: 'FieldsFormattingSymbolsRepairer',
  name: 'String fields: Formatting Symbols',
  description: 'Removes formatting symbols (new lines, tabs, etc.) in plain string fields.',
  configs: [],
};

const FIELDS_INVALID_ENUMERATION_VALUE: Repairer = {
  id: 'FieldsInvalidEnumerationValueRepairer',
  name: 'Enumeration fields: Invalid value',
  description:
    'Finds fields with invalid enumeration values. Repair removes the invalid value if an empty value is allowed, or if it is just one of several values.',
  configs: [
    {
      key: 'removeInvalidValues',
      description: 'Remove invalid values',
      hint: 'Clear/remove value if it is not defined in the specified enumeration',
      type: 'BOOLEAN',
      defaultValue: false,
    },
  ],
};

const FIELDS_INVALID_USER_VALUE: Repairer = {
  id: 'FieldsInvalidUserValueRepairer',
  name: 'User fields: Invalid value',
  description: 'Finds user fields with invalid values. Repair removes the invalid value if possible.',
  configs: [
    {
      key: 'removeInvalidValues',
      description: 'Remove invalid values',
      hint: "Clear/remove value if the user isn't found",
      type: 'BOOLEAN',
      defaultValue: false,
    },
  ],
};

const FIELDS_RICH_TEXT_LINKS: Repairer = {
  id: 'FieldsRichTextLinksRepairer',
  name: 'Rich text fields: Broken Links',
  description:
    'Finds links to work items in rich text fields and checks if the referenced work item exists. If the link points to a work item in another project and that work item does not exist, the project part (data-scope) of the link is removed (if the work item exists in the current project).',
  configs: LINK_CONFIGS,
};

const FIELDS_WRONG_TYPE: Repairer = {
  id: 'FieldsWrongTypeRepairer',
  name: 'Fields: Wrong value type',
  description:
    "Converts values to the fields specific format (e.g. plain string fields may contain 'Text' objects left by changing type of the field from multi-line).",
  configs: [],
};

const MODULE_CONTENT_LINKS: Repairer = {
  id: 'ModuleContentLinksRepairer',
  name: 'Document content: Broken Links',
  description:
    'Finds links to work items in the document body and checks if the referenced work item exists. If the link points to a work item in another project and that work item does not exist, the project part (data-scope) of the link is removed (if the work item exists in the current project).',
  configs: LINK_CONFIGS,
};

const MODULE_DUPLICATE_LAYOUT_DECLARATION: Repairer = {
  id: 'ModuleDuplicateLayoutDeclarationRepairer',
  name: 'Document content: Duplicate layout declarations',
  description:
    'Checks if the document has several declarations of the same layout type. After repair only the first declaration will be kept and used for all items of the given type.',
  configs: [],
};

const MODULE_MISSING_TITLE_HEADING: Repairer = {
  id: 'ModuleMissingTitleHeadingRepairer',
  name: 'Document content: Missing title-heading',
  description:
    'Checks if the document has a title-heading defined and adds it if missing. A missing title-heading may cause severe issues, e.g. during import/export roundtrips.',
  configs: [],
};

const MODULE_NON_EXISTENT_WORK_ITEMS: Repairer = {
  id: 'ModuleNonExistentWorkItemsRepairer',
  name: 'Document: Non-existent Work Items',
  description: 'Finds unresolvable/invalid work items in the document body.',
  configs: [],
};

const MODULE_STANDARD_STRUCTURE_LINK_ROLE: Repairer = {
  id: 'ModuleStandardStructureLinkRoleRepairer',
  name: 'Standard structure link role',
  description:
    "Check whether standard ('parent') link role was chosen during document creation. No automatic repair possible - only manual actions are implied.",
  configs: [],
};

const MODULE_TABLES_AND_FIGURES_CAPTION: Repairer = {
  id: 'ModuleTablesAndFiguresCaptionRepairer',
  name: 'Document content: ToT/ToF captions',
  description:
    'Finds captions of tables and figures in the document body which are misaligned with internal identifiers. Usually this happens when captions are modified manually. As a result Table of tables/figures contain unexpected caption. Repairing such items mean replacing internal identifier with the modified label.',
  configs: [],
};

const MODULE_WRONG_LAYOUT_ASSIGNMENTS: Repairer = {
  id: 'ModuleWrongLayoutAssignmentsRepairer',
  name: 'Document content: Wrong layout assignments',
  description:
    "Checks if the assigned layout ID for a work item matches the layout declaration for its specific work item type. If the document doesn't contain declaration for a work item type it will be added.",
  configs: [],
};

const MODULE_WRONG_TITLE_HEADING_POSITION: Repairer = {
  id: 'ModuleWrongTitleHeadingPositionRepairer',
  name: 'Document content: Wrong title-heading position',
  description:
    'Checks if the document has wrong title-heading position. Title-heading must be the first element in the document body, but if the first page contains macros and empty strings only - title-heading can be moved to the beginning of the second page.',
  configs: [],
};

// COLLECTION and DOCUMENT map to identical lists in XmlRepairPolarionService (a collection is scanned by
// running the document repairers over each of its documents), so one array serves both mappings here.
const MODULE_REPAIRERS: Repairer[] = [
  MODULE_CONTENT_LINKS,
  MODULE_DUPLICATE_LAYOUT_DECLARATION,
  MODULE_MISSING_TITLE_HEADING,
  MODULE_TABLES_AND_FIGURES_CAPTION,
  MODULE_WRONG_LAYOUT_ASSIGNMENTS,
  MODULE_WRONG_TITLE_HEADING_POSITION,
  MODULE_NON_EXISTENT_WORK_ITEMS,
  MODULE_STANDARD_STRUCTURE_LINK_ROLE,
  FIELDS_FORMATTING_SYMBOLS,
  FIELDS_INVALID_ENUMERATION_VALUE,
  FIELDS_INVALID_USER_VALUE,
  FIELDS_RICH_TEXT_LINKS,
  FIELDS_WRONG_TYPE,
];

/** Mirrors XmlRepairPolarionService.REPAIRERS - the response of `/repairers?entityType=<type>`. */
export const REPAIRERS_BY_ENTITY_TYPE: Record<EntityType, Repairer[]> = {
  COLLECTION: MODULE_REPAIRERS,
  DOCUMENT: MODULE_REPAIRERS,
  WORKITEM: [
    BROKEN_LINKED_WORK_ITEMS,
    FIELDS_FORMATTING_SYMBOLS,
    FIELDS_INVALID_ENUMERATION_VALUE,
    FIELDS_INVALID_USER_VALUE,
    FIELDS_RICH_TEXT_LINKS,
    FIELDS_WRONG_TYPE,
  ],
};

/** Work items are the page default (see ENTITY_TYPE_OPTIONS/entityType in Repair.tsx). */
export const REPAIRERS: Repairer[] = REPAIRERS_BY_ENTITY_TYPE.WORKITEM;

/**
 * Repairer list for a `/repairers?entityType=<type>` URL, so a fetch mock answers an entity-type switch
 * the way the backend would. Falls back to the page default when the parameter is absent.
 */
export function repairersFor(url: string): Repairer[] {
  const entityType = new URL(url, window.location.origin).searchParams.get('entityType');
  return REPAIRERS_BY_ENTITY_TYPE[entityType as EntityType] ?? REPAIRERS;
}

export const WORK_ITEM_TYPES = [
  { id: 'requirement', name: 'Requirement', iconURL: '/polarion/icons/req.svg' },
  { id: 'task', name: 'Task', iconURL: '/polarion/icons/task.svg' },
];

export const DOCUMENT_TYPES = [{ id: 'generic', name: 'Generic', iconURL: null }];

export const BASELINES = [
  { revision: '4321', name: 'Release 1.0' },
  { revision: '0', name: 'invalid' },
];

// A scan result exercising: a repairable work item with two issues + a warning, a work item pinned to
// a revision (non-repairable), and a collection with two sub-items that have issues. Each issue names a
// repairer that really applies to its entity type (see REPAIRERS_BY_ENTITY_TYPE), so the repairer
// breakdown and the per-issue repairer labels show realistic combinations. The sub-item repairers are
// picked from the intersection of the document and work item mappings: this one fixture answers the scan
// of any entity type, and a repairer missing from the loaded list would render as its raw id (that
// fallback is covered on its own in IssueList.test.tsx).
export const SCAN_RESULT: ScanResult = {
  report: 'Scanned 4 entities in 1.2s',
  items: [
    {
      entityType: 'WORKITEM',
      projectId: 'elibrary',
      space: null,
      entityId: 'EL-100',
      revision: null,
      issues: [
        {
          metaInfo: 'meta-1',
          repairer: 'FieldsInvalidEnumerationValueRepairer',
          description: 'Bad enum in EL-100',
          warnings: [],
        },
        {
          metaInfo: 'meta-2',
          repairer: 'BrokenLinkedWorkItemsRepairer',
          description: 'Broken linked work item in EL-100',
          warnings: [],
        },
      ],
      fields: {},
      subitems: [],
      warnings: ['Heads up: EL-100 has a soft warning'],
    },
    {
      entityType: 'WORKITEM',
      projectId: 'elibrary',
      space: null,
      entityId: 'EL-200',
      revision: '4321',
      issues: [
        {
          metaInfo: 'meta-3',
          repairer: 'FieldsInvalidEnumerationValueRepairer',
          description: 'Bad enum in EL-200',
          warnings: [],
        },
      ],
      fields: {},
      subitems: [],
      warnings: [],
    },
    {
      entityType: 'COLLECTION',
      projectId: 'elibrary',
      space: 'coll',
      entityId: 'COLL-1',
      revision: null,
      issues: [],
      fields: {},
      warnings: [],
      subitems: [
        {
          entityType: 'DOCUMENT',
          projectId: 'elibrary',
          space: 'spaceA',
          entityId: 'DOC-1',
          revision: null,
          issues: [
            {
              metaInfo: 'meta-4',
              repairer: 'FieldsInvalidEnumerationValueRepairer',
              description: 'Bad enum in DOC-1',
              warnings: [],
            },
          ],
          fields: {},
          subitems: [],
          warnings: [],
        },
        {
          entityType: 'DOCUMENT',
          projectId: 'elibrary',
          space: 'spaceA',
          entityId: 'DOC-2',
          revision: null,
          issues: [
            {
              metaInfo: 'meta-5',
              repairer: 'FieldsRichTextLinksRepairer',
              description: 'Broken rich text link in DOC-2',
              warnings: [],
            },
          ],
          fields: {},
          subitems: [],
          warnings: [],
        },
      ],
    },
  ],
};
