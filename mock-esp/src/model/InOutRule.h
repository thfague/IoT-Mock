#ifndef INOUTRULE_H
#define INOUTRULE_H

#include "Rule.h"

class InOutRule : public Rule {
 public:
  InOutRule(){};
  InOutRule(Request request, Response response);
};

#endif
