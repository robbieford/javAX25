#!/usr/bin/env python
# -*- coding: utf-8 -*-

# ***************************************************************************
# *   Copyright (C) 2011, Paul Lutus                                        *
# *                                                                         *
# *   This program is free software; you can redistribute it and/or modify  *
# *   it under the terms of the GNU General Public License as published by  *
# *   the Free Software Foundation; either version 2 of the License, or     *
# *   (at your option) any later version.                                   *
# *                                                                         *
# *   This program is distributed in the hope that it will be useful,       *
# *   but WITHOUT ANY WARRANTY; without even the implied warranty of        *
# *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
# *   GNU General Public License for more details.                          *
# *                                                                         *
# *   You should have received a copy of the GNU General Public License     *
# *   along with this program; if not, write to the                         *
# *   Free Software Foundation, Inc.,                                       *
# *   59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.             *
# ***************************************************************************

from biquad_module import Biquad

from pylab import *

import re

def ntrp(x,xa,xb,ya,yb):
  return (x-xa) * (yb-ya) / (xb-xa) + ya

sample_rate = 48000.0 # sampling frequency

cf = 1700
dev = 500
start_f = cf - dev
end_f = cf + dev
modi = 0

pll_integral = 0
pll_lock = 0
old_ref = 0
pll_cf = 1700
pll_loop_gain = 0.5
ref_sig = 0

invsqr2 = 1.0 / math.sqrt(2.0)

output_lowpass = Biquad(20,sample_rate,invsqr2)

fa = []
da = []
xAx = []
testSigOut = []
pllRefOut = []
loopCont = []

dur = 0.00625

halfway = int(sample_rate*dur /2);

for n in range(int(sample_rate * dur)):
  t = n / sample_rate

  # BEGIN test signal block
  if n < halfway/2:
#    sweep_freq = ntrp(t,0,dur,start_f,end_f)
#    modi += (sweep_freq-cf) / (cf * sample_rate)
#    test_signal = math.sin(2 * math.pi * cf * (t + modi))
    test_signal = math.sin(2 * math.pi * 1200 * (t))
  elif n < halfway:
    test_signal = math.sin(2 * math.pi * 2200 * (t))
  elif n < halfway * 3 / 2:
    test_signal = math.sin(2 * math.pi * 1200 * (t))
  else:
    test_signal = math.sin(2 * math.pi * 2200 * (t))
  # END test signal block
  
  # BEGIN PLL block
  pll_loop_control = test_signal * ref_sig * pll_loop_gain
  output = output_lowpass(pll_loop_control)
  pll_integral += pll_loop_control / sample_rate
  ref_sig = math.sin(2 * math.pi * pll_cf * (t + pll_integral))
  # END PLL block
  
  fa.append(n)
  da.append(output)
  xAx.append(n)
  testSigOut.append(test_signal)
  pllRefOut.append(ref_sig)
  loopCont.append(pll_loop_control)

#plot(fa,da)
#ylim(-1,1)
#grid(True)
#locs, labels = xticks()
#setp(labels,fontsize=8)
#locs, labels = yticks()
#setp(labels,fontsize=8)
#yticks([-1,-0.75,-0.5,-0.25,0,0.25,0.5,0.75,1])
#gcf().set_size_inches(4,3)

#name = re.sub('.*?(\w+).*','\\1',sys.argv[0])
#savefig(name+'.png')

#show()
plot(xAx, testSigOut, 'r', xAx, loopCont, 'g')
show()
