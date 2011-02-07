/**
 * ***************************************************************************
 * Copyright (c) 2010 Qcadoo Limited
 * Project: Qcadoo MES
 * Version: 0.2.0
 *
 * This file is part of Qcadoo.
 *
 * Qcadoo is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation; either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 * ***************************************************************************
 */

package com.qcadoo.mes.products;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.qcadoo.mes.api.DataDefinitionService;
import com.qcadoo.mes.api.Entity;
import com.qcadoo.mes.api.SecurityService;
import com.qcadoo.mes.api.TranslationService;
import com.qcadoo.mes.internal.DefaultEntity;
import com.qcadoo.mes.internal.EntityTree;
import com.qcadoo.mes.model.DataDefinition;
import com.qcadoo.mes.model.search.Restrictions;
import com.qcadoo.mes.model.search.SearchCriteriaBuilder;
import com.qcadoo.mes.model.search.SearchResult;
import com.qcadoo.mes.products.util.NumberGeneratorService;
import com.qcadoo.mes.utils.ExpressionUtil;
import com.qcadoo.mes.view.ComponentState;
import com.qcadoo.mes.view.ComponentState.MessageType;
import com.qcadoo.mes.view.ViewDefinitionState;
import com.qcadoo.mes.view.components.FieldComponentState;
import com.qcadoo.mes.view.components.form.FormComponentState;
import com.qcadoo.mes.view.components.grid.GridComponentState;
import com.qcadoo.mes.view.components.lookup.LookupComponentState;

@Service
public final class OrderService {

    @Autowired
    private DataDefinitionService dataDefinitionService;

    @Autowired
    private SecurityService securityService;

    @Autowired
    private TranslationService translationService;

    @Autowired
    private NumberGeneratorService numberGeneratorService;

    public boolean clearOrderDatesAndWorkersOnCopy(final DataDefinition dataDefinition, final Entity entity) {
        entity.setField("state", "01pending");
        entity.setField("effectiveDateFrom", new Date());
        entity.setField("startWorker", securityService.getCurrentUserName());
        entity.setField("effectiveDateTo", null);
        entity.setField("endWorker", null);
        entity.setField("effectiveDateFrom", null);
        entity.setField("startWorker", null);
        entity.setField("doneQuantity", null);
        return true;
    }

    public void printOrder(final ViewDefinitionState viewDefinitionState, final ComponentState state, final String[] args) {
        if (state.getFieldValue() instanceof Long) {
            Entity order = dataDefinitionService.get("products", "order").get((Long) state.getFieldValue());
            if (order == null) {
                state.addMessage(translationService.translate("core.message.entityNotFound", state.getLocale()),
                        MessageType.FAILURE);
            } else {
                viewDefinitionState.redirectTo("/products/order." + args[0] + "?id=" + state.getFieldValue(), true);
            }

        } else {
            if (state instanceof FormComponentState) {
                state.addMessage(translationService.translate("core.form.entityWithoutIdentifier", state.getLocale()),
                        MessageType.FAILURE);
            } else {
                state.addMessage(translationService.translate("core.grid.noRowSelectedError", state.getLocale()),
                        MessageType.FAILURE);
            }
        }
    }

    public void changeOrderProduct(final ViewDefinitionState viewDefinitionState, final ComponentState state, final String[] args) {
        if (!(state instanceof LookupComponentState)) {
            return;
        }

        LookupComponentState product = (LookupComponentState) state;
        LookupComponentState technology = (LookupComponentState) viewDefinitionState.getComponentByReference("technology");
        FieldComponentState defaultTechnology = (FieldComponentState) viewDefinitionState
                .getComponentByReference("defaultTechnology");

        defaultTechnology.setFieldValue("");
        technology.setFieldValue(null);

        if (product.getFieldValue() != null) {
            Entity defaultTechnologyEntity = getDefaultTechnology(product.getFieldValue());

            if (defaultTechnologyEntity != null) {
                technology.setFieldValue(defaultTechnologyEntity.getId());
            }
        }
    }

    public void setAndDisableState(final ViewDefinitionState state, final Locale locale) {
        FormComponentState form = (FormComponentState) state.getComponentByReference("form");
        FieldComponentState orderState = (FieldComponentState) state.getComponentByReference("state");

        orderState.setEnabled(false);

        if (form.getEntityId() != null) {
            return;
        }

        orderState.setFieldValue("01pending");
    }

    public void activateOrder(final ViewDefinitionState viewDefinitionState, final ComponentState state, final String[] args) {
        if (state.getFieldValue() != null) {
            DataDefinition orderDataDefinition = dataDefinitionService.get("products", "order");
            Entity order = orderDataDefinition.get((Long) state.getFieldValue());

            if (state instanceof FormComponentState) {
                FieldComponentState orderState = (FieldComponentState) viewDefinitionState.getComponentByReference("state");

                if (Boolean.parseBoolean(args[0])) {
                    orderState.setFieldValue("02inProgress");
                } else {
                    if (!checkRequiredBatch(order)) {
                        state.addMessage(translationService.translate("genealogies.message.batchNotFound", state.getLocale()),
                                MessageType.FAILURE);
                        return;
                    }

                    orderState.setFieldValue("03done");
                }

                ((FormComponentState) state).performEvent(viewDefinitionState, "save", new String[0]);
            } else if (state instanceof GridComponentState) {
                if (Boolean.parseBoolean(args[0])) {
                    order.setField("state", "02inProgress");
                } else {
                    if (!checkRequiredBatch(order)) {
                        state.addMessage(translationService.translate("genealogies.message.batchNotFound", state.getLocale()),
                                MessageType.INFO);
                        return;
                    }

                    order.setField("state", "03done");
                }

                orderDataDefinition.save(order);

                ((GridComponentState) state).performEvent(viewDefinitionState, "refresh", new String[0]);
            }
        } else {
            if (state instanceof FormComponentState) {
                state.addMessage(translationService.translate("core.form.entityWithoutIdentifier", state.getLocale()),
                        MessageType.FAILURE);
            } else {
                state.addMessage(translationService.translate("core.grid.noRowSelectedError", state.getLocale()),
                        MessageType.FAILURE);
            }
        }
    }

    public void generateQualityControl(final ViewDefinitionState viewDefinitionState, final ComponentState state,
            final String[] args) {
        if (state.getFieldValue() != null) {
            DataDefinition orderDataDefinition = dataDefinitionService.get("products", "order");
            Entity order = orderDataDefinition.get((Long) state.getFieldValue());

            Entity technology = (Entity) order.getField("technology");

            generateQualityControlForGivenType(technology, order, viewDefinitionState);

        } else {
            if (state instanceof FormComponentState) {
                state.addMessage(translationService.translate("core.form.entityWithoutIdentifier", state.getLocale()),
                        MessageType.FAILURE);
            } else {
                state.addMessage(translationService.translate("core.grid.noRowSelectedError", state.getLocale()),
                        MessageType.FAILURE);
            }
        }
    }

    private void generateQualityControlForGivenType(final Entity technology, final Entity order, final ViewDefinitionState state) {

        String qualityControlType = technology.getField("qualityControlType").toString();

        if (qualityControlType.equals("01forBatch")) {
            List<Entity> genealogies = getGenealogiesForOrder(order.getId());

            for (Entity genealogy : genealogies) {
                DataDefinition qualityForBatchDataDefinition = dataDefinitionService.get("products", "qualityForBatch");

                Entity forBatch = new DefaultEntity("products", "qualityForBatch");
                forBatch.setField("order", order);
                forBatch.setField("number", numberGeneratorService.generateNumber(state, "qualityForBatch"));
                forBatch.setField("batchNr", genealogy.getField("batch"));

                BigDecimal doneQuantity = (BigDecimal) order.getField("doneQuantity");
                BigDecimal plannedQuantity = (BigDecimal) order.getField("plannedQuantity");

                if (doneQuantity != null) {
                    forBatch.setField("controlledQuantity", doneQuantity);
                } else if (plannedQuantity != null) {
                    forBatch.setField("controlledQuantity", plannedQuantity);
                }

                DataDefinition staffDataDefinition = dataDefinitionService.get("basic", "staff");

                forBatch.setField("staff", staffDataDefinition.get(securityService.getCurrentUser().getId()));
                forBatch.setField("date", new Date());

                qualityForBatchDataDefinition.save(forBatch);
            }

        } else if (qualityControlType.equals("02forUnit")) {
            BigDecimal sampling = (BigDecimal) technology.getField("unitSamplingNr");

            BigDecimal doneQuantity = (BigDecimal) order.getField("doneQuantity");
            BigDecimal plannedQuantity = (BigDecimal) order.getField("plannedQuantity");
            BigDecimal numberOfControls = doneQuantity != null ? doneQuantity.divide(sampling) : plannedQuantity.divide(sampling);

            for (int i = 0; i <= numberOfControls.intValue(); i++) {
                DataDefinition qualityForUnitDataDefinition = dataDefinitionService.get("products", "qualityForUnit");

                Entity forUnit = new DefaultEntity("products", "qualityForUnit");
                forUnit.setField("order", order);
                forUnit.setField("number", numberGeneratorService.generateNumber(state, "qualityForUnit"));

                if (i < numberOfControls.intValue()) {
                    forUnit.setField("controlledQuantity", sampling);
                } else {
                    BigDecimal numberOfRemainders = doneQuantity != null ? doneQuantity.divideAndRemainder(sampling)[1]
                            : plannedQuantity.divideAndRemainder(sampling)[1];
                    forUnit.setField("controlledQuantity", numberOfRemainders);
                }
                // forBatch.setField("staff", securityService.getCurrentUser().getId());
                forUnit.setField("date", new Date());

                qualityForUnitDataDefinition.save(forUnit);
            }
        } else if (qualityControlType.equals("03forOrder")) {
            DataDefinition qualityForOrderDataDefinition = dataDefinitionService.get("products", "qualityForOrder");

            Entity forOrder = new DefaultEntity("products", "qualityForOrder");
            forOrder.setField("order", order);
            forOrder.setField("number", numberGeneratorService.generateNumber(state, "qualityForOrder"));
            // forOrder.setField("staff", securityService.getCurrentUser());
            forOrder.setField("date", new Date());

            qualityForOrderDataDefinition.save(forOrder);
        } else if (qualityControlType.equals("04forOperation")) {
            EntityTree tree = technology.getTreeField("operationComponents");
            for (Entity entity : tree) {
                if (entity.getField("qualityControlRequired") != null && (Boolean) entity.getField("qualityControlRequired")) {
                    DataDefinition qualityForOperationDataDefinition = dataDefinitionService.get("products",
                            "qualityForOperation");

                    Entity forOperation = new DefaultEntity("products", "qualityForOperation");
                    forOperation.setField("order", order);
                    forOperation.setField("number", numberGeneratorService.generateNumber(state, "qualityForOperation"));
                    forOperation.setField("operation", entity.getBelongsToField("operation"));

                    qualityForOperationDataDefinition.save(forOperation);
                }

            }
        }

    }

    private List<Entity> getGenealogiesForOrder(final Long id) {
        DataDefinition genealogyDD = dataDefinitionService.get("genealogies", "genealogy");

        SearchCriteriaBuilder searchCriteria = genealogyDD.find().restrictedWith(Restrictions.eq("order.id", id));

        return searchCriteria.list().getEntities();
    }

    public void generateOrderNumber(final ViewDefinitionState state, final Locale locale) {
        numberGeneratorService.generateAndInsertNumber(state, "order");
    }

    public void fillDefaultTechnology(final ViewDefinitionState state, final Locale locale) {
        LookupComponentState product = (LookupComponentState) state.getComponentByReference("product");
        FieldComponentState defaultTechnology = (FieldComponentState) state.getComponentByReference("defaultTechnology");

        if (product.getFieldValue() != null) {
            Entity defaultTechnologyEntity = getDefaultTechnology(product.getFieldValue());

            if (defaultTechnologyEntity != null) {
                String defaultTechnologyValue = ExpressionUtil.getValue(defaultTechnologyEntity, "#number + ' - ' + #name",
                        locale);
                defaultTechnology.setFieldValue(defaultTechnologyValue);
            }
        }
    }

    public void disableTechnologiesIfProductDoesNotAny(final ViewDefinitionState state, final Locale locale) {
        LookupComponentState product = (LookupComponentState) state.getComponentByReference("product");
        LookupComponentState technology = (LookupComponentState) state.getComponentByReference("technology");
        FieldComponentState defaultTechnology = (FieldComponentState) state.getComponentByReference("defaultTechnology");
        FieldComponentState plannedQuantity = (FieldComponentState) state.getComponentByReference("plannedQuantity");

        defaultTechnology.setEnabled(false);

        if (product.getFieldValue() == null || !hasAnyTechnologies(product.getFieldValue())) {
            technology.setEnabled(false);
            technology.setRequired(false);
            plannedQuantity.setRequired(false);
        } else {
            technology.setRequired(true);
            plannedQuantity.setRequired(true);
        }
    }

    public void disableFormForDoneOrder(final ViewDefinitionState state, final Locale locale) {
        FormComponentState order = (FormComponentState) state.getComponentByReference("form");
        LookupComponentState technology = (LookupComponentState) state.getComponentByReference("technology");

        boolean disabled = false;

        if (order.getEntityId() != null) {
            Entity entity = dataDefinitionService.get("products", "order").get(order.getEntityId());

            if (entity != null && "03done".equals(entity.getStringField("state")) && order.isValid()) {
                disabled = true;
            }
        }

        order.setEnabledWithChildren(!disabled);
        technology.setEnabled(!disabled);
    }

    public boolean checkOrderDates(final DataDefinition dataDefinition, final Entity entity) {
        return compareDates(dataDefinition, entity, "dateFrom", "dateTo");
    }

    public boolean checkOrderPlannedQuantity(final DataDefinition dataDefinition, final Entity entity) {
        Entity product = entity.getBelongsToField("product");
        if (product == null) {
            return true;
        }
        Object o = entity.getField("plannedQuantity");
        if (o == null) {
            entity.addError(dataDefinition.getField("plannedQuantity"), "products.validate.global.error.plannedQuantityError");
            return false;
        } else {
            return true;
        }
    }

    public boolean checkOrderTechnology(final DataDefinition dataDefinition, final Entity entity) {
        Entity product = entity.getBelongsToField("product");
        if (product == null) {
            return true;
        }
        if (entity.getField("technology") == null) {
            if (hasAnyTechnologies(product.getId())) {
                entity.addError(dataDefinition.getField("technology"), "products.validate.global.error.technologyError");
                return false;
            }
        }
        return true;
    }

    public void fillOrderDatesAndWorkers(final DataDefinition dataDefinition, final Entity entity) {
        if (("02inProgress".equals(entity.getField("state")) || "03done".equals(entity.getField("state")))
                && entity.getField("effectiveDateFrom") == null) {
            entity.setField("effectiveDateFrom", new Date());
            entity.setField("startWorker", securityService.getCurrentUserName());
        }
        if ("03done".equals(entity.getField("state")) && entity.getField("effectiveDateTo") == null) {
            entity.setField("effectiveDateTo", new Date());
            entity.setField("endWorker", securityService.getCurrentUserName());

        }
    }

    public boolean checkIfOrderHasTechnology(final DataDefinition dataDefinition, final Entity entity) {
        Entity order = entity.getBelongsToField("order");

        if (order == null) {
            return true;
        }

        if (order.getField("technology") == null) {
            entity.addError(dataDefinition.getField("order"), "products.validate.global.error.orderMustHaveTechnology");
            return false;
        } else {
            return true;
        }
    }

    public boolean checkIfOrderTechnologyHasOperations(final DataDefinition dataDefinition, final Entity entity) {
        Entity order = entity.getBelongsToField("order");

        if (order == null || order.getField("technology") == null) {
            return true;
        }

        if (order.getBelongsToField("technology").getTreeField("operationComponents").isEmpty()) {
            entity.addError(dataDefinition.getField("order"), "products.validate.global.error.orderTechnologyMustHaveOperation");
            return false;
        } else {
            return true;
        }
    }

    private Entity getDefaultTechnology(final Long selectedProductId) {
        DataDefinition instructionDD = dataDefinitionService.get("products", "technology");

        SearchCriteriaBuilder searchCriteria = instructionDD.find().withMaxResults(1)
                .restrictedWith(Restrictions.eq(instructionDD.getField("master"), true))
                .restrictedWith(Restrictions.belongsTo(instructionDD.getField("product"), selectedProductId));

        SearchResult searchResult = searchCriteria.list();

        if (searchResult.getTotalNumberOfEntities() == 1) {
            return searchResult.getEntities().get(0);
        } else {
            return null;
        }
    }

    private boolean hasAnyTechnologies(final Long selectedProductId) {
        DataDefinition technologyDD = dataDefinitionService.get("products", "technology");

        SearchCriteriaBuilder searchCriteria = technologyDD.find().withMaxResults(1)
                .restrictedWith(Restrictions.belongsTo(technologyDD.getField("product"), selectedProductId));

        SearchResult searchResult = searchCriteria.list();

        return (searchResult.getTotalNumberOfEntities() > 0);
    }

    private boolean compareDates(final DataDefinition dataDefinition, final Entity entity, final String dateFromField,
            final String dateToField) {
        Date dateFrom = (Date) entity.getField(dateFromField);
        Date dateTo = (Date) entity.getField(dateToField);

        if (dateFrom == null || dateTo == null) {
            return true;
        }

        if (dateFrom.after(dateTo)) {
            entity.addError(dataDefinition.getField(dateToField), "products.validate.global.error.datesOrder");
            return false;
        } else {
            return true;
        }
    }

    private boolean checkRequiredBatch(final Entity order) {
        Entity technology = (Entity) order.getField("technology");
        if (technology != null) {
            if (order.getHasManyField("genealogies").size() == 0) {
                if ((Boolean) technology.getField("batchRequired")) {
                    return false;
                }
                if ((Boolean) technology.getField("shiftFeatureRequired")) {
                    return false;
                }
                if ((Boolean) technology.getField("postFeatureRequired")) {
                    return false;
                }
                if ((Boolean) technology.getField("otherFeatureRequired")) {
                    return false;
                }
                for (Entity operationComponent : technology.getHasManyField("operationComponents")) {
                    for (Entity operationProductComponent : operationComponent.getHasManyField("operationProductInComponents")) {
                        if ((Boolean) operationProductComponent.getField("batchRequired")) {
                            return false;
                        }
                    }
                }
            }
            for (Entity genealogy : order.getHasManyField("genealogies")) {
                if ((Boolean) technology.getField("batchRequired")) {
                    if (genealogy.getField("batch") == null) {
                        return false;
                    }
                }
                if ((Boolean) technology.getField("shiftFeatureRequired")) {
                    List<Entity> entityList = genealogy.getHasManyField("shiftFeatures");
                    if (entityList.size() == 0) {
                        return false;
                    }
                }
                if ((Boolean) technology.getField("postFeatureRequired")) {
                    List<Entity> entityList = genealogy.getHasManyField("postFeatures");
                    if (entityList.size() == 0) {
                        return false;
                    }
                }
                if ((Boolean) technology.getField("otherFeatureRequired")) {
                    List<Entity> entityList = genealogy.getHasManyField("otherFeatures");
                    if (entityList.size() == 0) {
                        return false;
                    }
                }
                for (Entity genealogyProductIn : genealogy.getHasManyField("productInComponents")) {
                    if ((Boolean) ((Entity) genealogyProductIn.getField("productInComponent")).getField("batchRequired")) {
                        List<Entity> entityList = genealogyProductIn.getHasManyField("batch");
                        if (entityList.size() == 0) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

}
